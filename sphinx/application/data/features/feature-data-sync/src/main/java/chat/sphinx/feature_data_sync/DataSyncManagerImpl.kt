package chat.sphinx.feature_data_sync

import chat.sphinx.concept_data_sync.DataSyncManager
import chat.sphinx.concept_data_sync.DataSyncManagerListener
import chat.sphinx.concept_data_sync.model.SyncStatus
import chat.sphinx.concept_meme_server.MemeServerTokenHandler
import chat.sphinx.concept_network_query_meme_server.NetworkQueryMemeServer
import chat.sphinx.concept_repository_data_sync.DataSyncRepository
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import chat.sphinx.kotlin_response.message
import chat.sphinx.wrapper_common.dashboard.ChatId
import chat.sphinx.wrapper_common.DateTime
import chat.sphinx.wrapper_common.dashboard.ContactId
import chat.sphinx.wrapper_common.datasync.DataSync
import chat.sphinx.wrapper_common.datasync.DataSyncIdentifier
import chat.sphinx.wrapper_common.datasync.DataSyncKey
import chat.sphinx.wrapper_common.datasync.DataSyncValue
import chat.sphinx.wrapper_common.datasync.FeedItemStatus
import chat.sphinx.wrapper_common.datasync.FeedStatus
import chat.sphinx.wrapper_common.datasync.ItemsResponse
import chat.sphinx.wrapper_common.datasync.ItemsResponse.Companion.toItemsResponse
import chat.sphinx.wrapper_common.datasync.SettingItem
import chat.sphinx.wrapper_common.datasync.TimezoneSetting
import chat.sphinx.wrapper_common.lightning.Sat
import chat.sphinx.wrapper_contact.Contact
import chat.sphinx.wrapper_meme_server.AuthenticationToken
import chat.sphinx.wrapper_message_media.token.MediaHost
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet

class DataSyncManagerImpl (
    private val moshi: Moshi,
    private val dispatchers: CoroutineDispatchers,
    private val networkQueryMemeServer: NetworkQueryMemeServer,
    private val memeServerTokenHandler: MemeServerTokenHandler,
): DataSyncManager() {

    private var accountOwner: StateFlow<Contact?>? = null

    fun setAccountOwner(owner: StateFlow<Contact?>) {
        this.accountOwner = owner
    }

    private val syncMutex = Mutex()
    private val synchronizedListeners = SynchronizedListenerHolder()
    private val listeners = CopyOnWriteArraySet<DataSyncManagerListener>()

    private val _syncStatusStateFlow = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val syncStatusStateFlow: StateFlow<SyncStatus>
        get() = _syncStatusStateFlow.asStateFlow()

    private val _dataSyncStateFlow: MutableStateFlow<List<DataSync>> by lazy {
        MutableStateFlow(emptyList())
    }

    override val dataSyncStateFlow: StateFlow<List<DataSync>>
        get() = _dataSyncStateFlow.asStateFlow()

    override fun updateDataSyncList(dataSyncList: List<DataSync>) {
        _dataSyncStateFlow.value = dataSyncList
    }

    // Listener Management
    private inner class SynchronizedListenerHolder {
        private val listeners: LinkedHashSet<DataSyncManagerListener> = LinkedHashSet()

        fun addListener(listener: DataSyncManagerListener): Boolean = synchronized(this) {
            listeners.add(listener)
        }

        fun removeListener(listener: DataSyncManagerListener): Boolean = synchronized(this) {
            listeners.remove(listener)
        }

        fun forEachListener(action: (DataSyncManagerListener) -> Unit) {
            synchronized(this) {
                listeners.forEach(action)
            }
        }
    }

    override fun addListener(listener: DataSyncManagerListener): Boolean =
        synchronizedListeners.addListener(listener)

    override fun removeListener(listener: DataSyncManagerListener): Boolean =
        synchronizedListeners.removeListener(listener)

    private fun notifyListeners(action: DataSyncManagerListener.() -> Unit) {
        synchronizedListeners.forEachListener { listener ->
            action(listener)
        }
    }

    // Save methods - notify listeners to save to DB
    override suspend fun saveTipAmount(value: String) {
        saveDataSyncItem(
            key = DataSyncKey.TIP_AMOUNT,
            identifier = "0",
            value = value
        )
    }

    override suspend fun savePrivatePhoto(value: String) {
        saveDataSyncItem(
            key = DataSyncKey.PRIVATE_PHOTO,
            identifier = "0",
            value = value
        )
    }

    override suspend fun saveTimezoneForChat(
        chatPubkey: String,
        timezoneEnabled: Boolean,
        timezoneIdentifier: String
    ) {
        val timezone = TimezoneSetting(timezoneEnabled, timezoneIdentifier)
        val jsonString = timezone.toJson(moshi)
        saveDataSyncItem(
            key = DataSyncKey.TIMEZONE,
            identifier = chatPubkey,
            value = jsonString
        )
    }

    override suspend fun saveFeedStatus(
        feedId: String,
        chatPubkey: String,
        feedUrl: String,
        subscribed: Boolean,
        satsPerMinute: Int,
        playerSpeed: Double,
        itemId: String
    ) {
        val feedStatus = FeedStatus(chatPubkey, feedUrl, feedId, subscribed, satsPerMinute, playerSpeed, itemId)
        val jsonString = feedStatus.toJson(moshi)
        saveDataSyncItem(
            key = DataSyncKey.FEED_STATUS,
            identifier = feedId,
            value = jsonString
        )
    }

    override suspend fun saveFeedItemStatus(
        feedId: String,
        itemId: String,
        duration: Int,
        currentTime: Int
    ) {
        val feedItemStatus = FeedItemStatus(duration, currentTime)
        val jsonString = feedItemStatus.toJson(moshi)
        saveDataSyncItem(
            key = DataSyncKey.FEED_ITEM_STATUS,
            identifier = "$feedId-$itemId",
            value = jsonString
        )
    }

    private suspend fun saveDataSyncItem(
        key: String,
        identifier: String,
        value: String
    ) {
        notifyListeners {
            onSaveDataSyncItem(
                key = key,
                identifier = identifier,
                value = value,
                timestamp = getTimestampInMilliseconds()
            )
        }

        // Then sync with server
        syncWithServer()
    }

    override suspend fun syncWithServer() = syncMutex.withLock {
        withContext(dispatchers.io) {
            try {
                _syncStatusStateFlow.value = SyncStatus.Syncing

                // Get server items
                val serverDataString = getFileFromServer()

                if (serverDataString != null) {
                    val itemsResponse = serverDataString.toItemsResponse(moshi)

                    // Notify listeners to merge server data
//                    notifyListeners {
//                        onServerDataReceived(itemsResponse.items)
//                    }

                    // Upload local changes to server
//                    uploadLocalDataToServer(localItems)
                }

                _syncStatusStateFlow.value = SyncStatus.Success(System.currentTimeMillis())

            } catch (e: Exception) {
                _syncStatusStateFlow.value = SyncStatus.Error(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun requestLocalDataSyncItems(): List<SettingItem> {
        // Request from listener (repository will provide)
        var items: List<SettingItem> = emptyList()

//        notifyListeners {
//            items = onRequestLocalDataSyncItems()
//        }

        return items
    }

    private suspend fun uploadLocalDataToServer(localItems: List<SettingItem>) {
        try {
            val token = getAuthenticationToken() ?: return

            val itemsResponse = ItemsResponse(items = localItems)

//            networkQueryMemeServer.uploadDataSyncFile(
//                authenticationToken = token,
//                memeServerHost = MediaHost.DEFAULT,
//                data = itemsResponse.toJson(moshi)
//            ).collect { loadResponse ->
//                when (loadResponse) {
//                    is LoadResponse.Loading -> {}
//                    is Response.Error -> {
//                        println("Error uploading data sync: ${loadResponse.message}")
//                    }
//                    is Response.Success -> {
//                        println("Successfully uploaded data sync")
//                    }
//                }
//            }
        } catch (e: Exception) {
            println("Exception uploading data sync: ${e.message}")
        }
    }

    private suspend fun getFileFromServer(): String? {
        return try {
            val token = getAuthenticationToken() ?: return null

            var resultJson: String? = null

            networkQueryMemeServer.getDataSyncFile(
                authenticationToken = token,
                memeServerHost = MediaHost.DEFAULT
            ).collect { loadResponse ->
                when (loadResponse) {
                    is LoadResponse.Loading -> {}
                    is Response.Error -> {
                        println("Error fetching data sync: ${loadResponse.message}")
                    }
                    is Response.Success -> {
                        resultJson = loadResponse.value.toJson()
                    }
                }
            }

            resultJson
        } catch (e: Exception) {
            println("Exception fetching data sync: ${e.message}")
            null
        }
    }

    private fun getTimestampInMilliseconds(): Long =
        System.currentTimeMillis()

    private suspend fun getAuthenticationToken(): AuthenticationToken? {
        return try {
            val memeServerHost = MediaHost.DEFAULT
            val token = memeServerTokenHandler.retrieveAuthenticationToken(memeServerHost)
                ?: return null

            token
        } catch (e: Exception) {
            println("Exception retrieving authentication token: ${e.message}")
            null
        }
    }
}