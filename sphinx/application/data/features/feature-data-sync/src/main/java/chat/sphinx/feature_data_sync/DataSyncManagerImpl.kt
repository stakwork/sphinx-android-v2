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

    enum class SettingKey(val value: String) {
        TIP_AMOUNT("tip_amount"),
        PRIVATE_PHOTO("private_photo"),
        TIMEZONE("timezone"),
        FEED_STATUS("feed_status"),
        FEED_ITEM_STATUS("feed_item_status")
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

        suspend fun getFirstAvailableListener(): DataSyncManagerListener? {
            return synchronized(this) {
                listeners.firstOrNull()
            }
        }
    }

    override fun addListener(listener: DataSyncManagerListener): Boolean =
        synchronizedListeners.addListener(listener)

    override fun removeListener(listener: DataSyncManagerListener): Boolean =
        synchronizedListeners.removeListener(listener)

    private  fun notifyListeners(action: DataSyncManagerListener.() -> Unit) {
        synchronizedListeners.forEachListener { listener ->
            action(listener)
        }
    }

    // Save methods
    override suspend fun saveTipAmount(value: String) {
        saveDataSyncItem(
            key = SettingKey.TIP_AMOUNT.value,
            identifier = "0",
            value = value
        )
    }

    override suspend fun savePrivatePhoto(value: String) {
        saveDataSyncItem(
            key = SettingKey.PRIVATE_PHOTO.value,
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
            key = SettingKey.TIMEZONE.value,
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
            key = SettingKey.FEED_STATUS.value,
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
            key = SettingKey.FEED_ITEM_STATUS.value,
            identifier = "$feedId-$itemId",
            value = jsonString
        )
    }

    private suspend fun saveDataSyncItem(
        key: String,
        identifier: String,
        value: String
    ) {
        // First, fetch existing data from server
        val serverDataString = getFileFromServer()

        notifyListeners {
            onSaveDataSyncItem(
                key = key,
                identifier = identifier,
                value = value,
                timestamp = 0
            )
        }

        syncWithServer()
    }

    override suspend fun syncWithServer() = syncMutex.withLock {
        withContext(dispatchers.io) {
            try {
                _syncStatusStateFlow.value = SyncStatus.Syncing

//                val serverDataString = getFileFromServer() ?: run {
//                    _syncStatusStateFlow.value = SyncStatus.Error("Failed to fetch server file")
//                    return@withContext
//                }
//
//                val itemsResponse = serverDataString.toItemsResponse(moshi)
//                val dbItems = dataSyncRepository?.getAllDataSync?.firstOrNull() ?: emptyList()
//
//                // Find items that exist on server but not locally
//                val missingItems = findMissingItems(dbItems, itemsResponse.items)
//
//                // Notify listeners of remote changes
//                for (item in missingItems) {
//                    notifyListenersOfRemoteChange(item)
//                }

                _syncStatusStateFlow.value = SyncStatus.Success(System.currentTimeMillis())

            } catch (e: Exception) {
                _syncStatusStateFlow.value = SyncStatus.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun findMissingItems(
        localItems: List<DataSync>,
        serverItems: List<SettingItem>
    ): List<SettingItem> {
        val localSet = localItems.map { "${it.sync_key}-${it.identifier}" }.toSet()
        return serverItems.filter { item ->
            val combinedKey = "${item.key}-${item.identifier}"
            !localSet.contains(combinedKey)
        }
    }

    private suspend fun notifyListenersOfRemoteChange(item: SettingItem) {
        when (SettingKey.values().find { it.value == item.key }) {
            SettingKey.TIP_AMOUNT -> {
                item.value.asInt()?.let { tipAmount ->
                    listeners.forEach { it.onRemoteTipAmountChanged(tipAmount.toLong()) }
                }
            }

            SettingKey.PRIVATE_PHOTO -> {
                item.value.asBool()?.let { isPrivate ->
                    listeners.forEach { it.onRemotePrivatePhotoChanged(isPrivate) }
                }
            }

            SettingKey.TIMEZONE -> {
                item.value.asTimezone()?.let { timezone ->
                    listeners.forEach {
                        it.onRemoteTimezoneChanged(
                            item.identifier,
                            timezone.timezoneEnabled,
                            timezone.timezoneIdentifier
                        )
                    }
                }
            }

            SettingKey.FEED_STATUS -> {
                item.value.asFeedStatus()?.let { feedStatus ->
                    listeners.forEach {
                        it.onRemoteFeedStatusChanged(
                            feedStatus.feedId,
                            feedStatus.chatPubkey,
                            feedStatus.feedUrl,
                            feedStatus.subscribed,
                            feedStatus.satsPerMinute,
                            feedStatus.playerSpeed,
                            feedStatus.itemId
                        )
                    }
                }
            }

            SettingKey.FEED_ITEM_STATUS -> {
                item.value.asFeedItemStatus()?.let { feedItemStatus ->
                    val parts = item.identifier.split("-")
                    if (parts.size == 2) {
                        listeners.forEach {
                            it.onRemoteFeedItemStatusChanged(
                                parts[0],
                                parts[1],
                                feedItemStatus.duration,
                                feedItemStatus.currentTime
                            )
                        }
                    }
                }
            }

            null -> {}
        }
    }

    // File operations - fetch from meme server
    private suspend fun getFileFromServer(): String? {
        return try {
            val token = getAuthenticationToken() ?: return null

            // Make network request to fetch data sync file
            var resultJson: String? = null

            networkQueryMemeServer.getDataSyncFile(
                authenticationToken = token,
                memeServerHost = MediaHost.DEFAULT
            ).collect { loadResponse ->
                when (loadResponse) {
                    is LoadResponse.Loading -> {
                    }
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
    private fun saveFileToServer(itemsResponse: ItemsResponse) {
        // TODO: Implement upload to meme server
    }

    private fun createDefaultFile(): String {
        val timestamp = (System.currentTimeMillis() / 1000.0) - 3600
        return """{"items": [{"key": "tip_amount", "identifier": "0", "date": "$timestamp", "value": "12"}]}"""
    }
}