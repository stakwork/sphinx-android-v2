package chat.sphinx.feature_data_sync

import chat.sphinx.concept_data_sync.DataSyncManager
import chat.sphinx.concept_data_sync.DataSyncManagerListener
import chat.sphinx.concept_data_sync.model.SyncStatus
import chat.sphinx.concept_meme_server.MemeServerTokenHandler
import chat.sphinx.concept_network_query_meme_server.NetworkQueryMemeServer
import chat.sphinx.concept_repository_data_sync.DataSyncRepository
import chat.sphinx.feature_data_sync.adapter.SettingItemRawAdapter
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import chat.sphinx.kotlin_response.message
import chat.sphinx.wrapper_common.dashboard.ChatId
import chat.sphinx.wrapper_common.DateTime
import chat.sphinx.wrapper_common.dashboard.ContactId
import chat.sphinx.wrapper_common.datasync.DataSync
import chat.sphinx.wrapper_common.datasync.DataSyncIdentifier
import chat.sphinx.wrapper_common.datasync.DataSyncJson
import chat.sphinx.wrapper_common.datasync.DataSyncKey
import chat.sphinx.wrapper_common.datasync.DataSyncValue
import chat.sphinx.wrapper_common.datasync.FeedItemStatus
import chat.sphinx.wrapper_common.datasync.FeedStatus
import chat.sphinx.wrapper_common.datasync.ItemsResponse
import chat.sphinx.wrapper_common.datasync.ItemsResponse.Companion.toItemsResponse
import chat.sphinx.wrapper_common.datasync.ItemsResponseRaw
import chat.sphinx.wrapper_common.datasync.SettingItem
import chat.sphinx.wrapper_common.datasync.TimezoneSetting
import chat.sphinx.wrapper_common.datasync.toItemsResponseRaw
import chat.sphinx.wrapper_common.datasync.toSettingItems
import chat.sphinx.wrapper_common.lightning.Sat
import chat.sphinx.wrapper_common.time
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

    private val dataSyncMoshi: Moshi by lazy {
        moshi.newBuilder()
            .add(SettingItemRawAdapter())
            .build()
    }

    private var accountOwner: StateFlow<Contact?>? = null

    fun setAccountOwner(owner: StateFlow<Contact?>) {
        this.accountOwner = owner
    }

    private val syncMutex = Mutex()
    private val synchronizedListeners = SynchronizedListenerHolder()

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

        // Suspend version for result-returning operations
        suspend fun <T> requestFromFirstListener(
            block: suspend (DataSyncManagerListener) -> T?
        ): T? {
            val listenersCopy = synchronized(this) {
                listeners.toList()
            }

            for (listener in listenersCopy) {
                val result = block(listener)
                if (result != null) {
                    return result
                }
            }

            return null
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

    // Generic helper for suspend operations that need results
    private suspend fun <T> requestFromListener(
        block: suspend (DataSyncManagerListener) -> T?
    ): T? {
        return synchronizedListeners.requestFromFirstListener(block)
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
        if (chatPubkey.isEmpty()) {
            return
        }

        val timezone = TimezoneSetting(
            timezoneEnabled = timezoneEnabled,
            timezoneIdentifier = if (timezoneEnabled) timezoneIdentifier else ""
        )

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
        withContext(dispatchers.io) {
            requestFromListener { listener ->
                listener.onSaveDataSyncItem(
                    key = key,
                    identifier = identifier,
                    value = value,
                    timestamp = getTimestampInMilliseconds()
                )
            }
        }

        syncWithServer()
    }

    // Encrypt value using listener
    private suspend fun encryptValue(value: String): String? {
        return requestFromListener { listener ->
            listener.onEncryptDataSync(value)
        }
    }

    // Decrypt value using listener
    private suspend fun decryptValue(value: String): String? {
        return requestFromListener { listener ->
            listener.onDecryptDataSync(value)
        }
    }

    override suspend fun syncWithServer() = syncMutex.withLock {
        withContext(dispatchers.io) {
            try {
                _syncStatusStateFlow.value = SyncStatus.Syncing

                val localDataSync = _dataSyncStateFlow.value
                val localItems = localDataSync.map { dataSync ->
                    SettingItem(
                        key = dataSync.sync_key.value,
                        identifier = dataSync.identifier.value,
                        date = convertTimestampToSeconds(dataSync.date.time),
                        value = parseLocalValue(
                            dataSync.sync_value.value,
                            dataSync.sync_key.value
                        )
                    )
                }

                val serverDataString = getFileFromServer()

                if (serverDataString != null) {
                    val decryptedData = decryptValue(serverDataString)

                    if (decryptedData != null) {
                        try {
                            // Use dataSyncMoshi instead of moshi
                            val serverItemsResponseRaw = decryptedData.toItemsResponseRaw(dataSyncMoshi)

                            val serverItems = serverItemsResponseRaw.toSettingItems()

                            serverItems.forEach { item ->
                                val valueStr = when (val value = item.value) {
                                    is DataSyncJson.StringValue -> "\"${value.value}\""
                                    is DataSyncJson.ObjectValue -> value.value.toString()
                                }
                            }

                            val mergedItems = mergeDataSyncItems(localItems, serverItems)

                            applyMergedItemsToLocal(mergedItems, localItems)
                            uploadMergedDataToServer(mergedItems)

                        } catch (e: Exception) {
                            e.printStackTrace()
                            _syncStatusStateFlow.value = SyncStatus.Error("Error parsing server data: ${e.message}")
                            return@withContext
                        }
                    } else {
                        if (localItems.isNotEmpty()) {
                            uploadMergedDataToServer(localItems)
                        }
                    }
                } else {
                    if (localItems.isNotEmpty()) {
                        uploadMergedDataToServer(localItems)
                    }
                }

                _syncStatusStateFlow.value = SyncStatus.Success(System.currentTimeMillis())

            } catch (e: Exception) {
                e.printStackTrace()
                _syncStatusStateFlow.value = SyncStatus.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun parseLocalValue(valueString: String, key: String): DataSyncJson {
        if (valueString.trim().startsWith("{")) {
            try {
                val map = parseSimpleJsonObject(valueString)
                return DataSyncJson.ObjectValue(map)
            } catch (e: Exception) {
            }
        }
        return DataSyncJson.StringValue(valueString)
    }

    // Simple JSON object parser for local data (only handles string values)
    private fun parseSimpleJsonObject(json: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val cleaned = json.trim().removePrefix("{").removeSuffix("}")

        if (cleaned.isEmpty()) return map

        cleaned.split(",").forEach { pair ->
            val parts = pair.split(":", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim().removeSurrounding("\"")
                val value = parts[1].trim().removeSurrounding("\"")
                map[key] = value
            }
        }

        return map
    }

    private fun mergeDataSyncItems(
        localItems: List<SettingItem>,
        serverItems: List<SettingItem>
    ): List<SettingItem> {
        val mergedMap = mutableMapOf<String, SettingItem>()

        // First, add all server items to the map
        serverItems.forEach { serverItem ->
            val key = "${serverItem.key}:${serverItem.identifier}"
            mergedMap[key] = serverItem
        }

        // Then process local items
        localItems.forEach { localItem ->
            val key = "${localItem.key}:${localItem.identifier}"
            val existingItem = mergedMap[key]

            if (existingItem == null) {
                // Item exists locally but not on server - add it
                mergedMap[key] = localItem
            } else {
                // Item exists in both - compare dates and keep the newest
                val localDate = parseDate(localItem.date)
                val serverDate = parseDate(existingItem.date)

                if (localDate > serverDate) {
                    // Local is newer, replace server item
                    mergedMap[key] = localItem
                }
                // Otherwise keep the server item (already in map)
            }
        }

        return mergedMap.values.toList()
    }

    private suspend fun applyMergedItemsToLocal(
        mergedItems: List<SettingItem>,
        localItems: List<SettingItem>
    ) {
        val localMap = localItems.associateBy { "${it.key}:${it.identifier}" }

        mergedItems.forEach { mergedItem ->
            val key = "${mergedItem.key}:${mergedItem.identifier}"
            val localItem = localMap[key]

            if (localItem == null) {
                // New item from server - save to local DB
                saveItemToLocal(mergedItem)
            } else {
                // Item exists locally - check if server version is newer
                val localDate = parseDate(localItem.date)
                val mergedDate = parseDate(mergedItem.date)

                if (mergedDate > localDate) {
                    // Server version is newer - update local DB
                    saveItemToLocal(mergedItem)
                }
            }
        }
    }


    private suspend fun saveItemToLocal(item: SettingItem) {
        requestFromListener { listener ->
            listener.onSaveDataSyncItem(
                key = item.key,
                identifier = item.identifier,
                value = item.value.toString(),
                timestamp = convertSecondsToMilliseconds(item.date)
            )
        }
    }

    private suspend fun uploadMergedDataToServer(mergedItems: List<SettingItem>) {
        try {
            val token = getAuthenticationToken() ?: return

            val itemsResponseRaw = mergedItems.toItemsResponseRaw()

            val adapter = dataSyncMoshi.adapter(ItemsResponseRaw::class.java)
            val jsonData = adapter.toJson(itemsResponseRaw)

            val encryptedData = encryptValue(jsonData)

            if (encryptedData == null) {
                return
            }

//            networkQueryMemeServer.uploadDataSyncFile(
//                authenticationToken = token,
//                memeServerHost = MediaHost.DEFAULT,
//                data = encryptedData
//            ).collect { loadResponse ->
//                when (loadResponse) {
//                    is LoadResponse.Loading -> {
//                        println("⏳ Uploading data sync...")
//                    }
//                    is Response.Error -> {
//                        println("❌ Error uploading data sync: ${loadResponse.message}")
//                    }
//                    is Response.Success -> {
//                        println("✅ Successfully uploaded data sync")
//                    }
//                }
//            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun convertTimestampToSeconds(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        return seconds.toString()
    }

    private fun convertSecondsToMilliseconds(seconds: String): Long {
        return try {
            val secondsValue = seconds.toDouble().toLong()
            secondsValue * 1000
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun parseDate(dateString: String): Long {
        return try {
            dateString.toDouble().toLong()
        } catch (e: Exception) {
            0L
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
                        val response = loadResponse.value
                        resultJson = response.toJson()
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