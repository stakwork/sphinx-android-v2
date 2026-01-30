package chat.sphinx.feature_data_sync

import chat.sphinx.concept_data_sync.DataSyncManager
import chat.sphinx.concept_data_sync.DataSyncManagerListener
import chat.sphinx.concept_data_sync.model.SyncStatus
import chat.sphinx.concept_meme_server.MemeServerTokenHandler
import chat.sphinx.concept_network_query_meme_server.NetworkQueryMemeServer
import chat.sphinx.feature_data_sync.adapter.SettingItemRawAdapter
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.message
import chat.sphinx.wrapper_common.datasync.*
import chat.sphinx.wrapper_common.time
import chat.sphinx.wrapper_contact.Contact
import chat.sphinx.wrapper_meme_server.AuthenticationToken
import chat.sphinx.wrapper_message_media.token.MediaHost
import com.squareup.moshi.Moshi
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.Base64
import java.util.*

class DataSyncManagerImpl(
    private val moshi: Moshi,
    private val dispatchers: CoroutineDispatchers,
    private val networkQueryMemeServer: NetworkQueryMemeServer,
    private val memeServerTokenHandler: MemeServerTokenHandler,
) : DataSyncManager() {

    private val dataSyncMoshi: Moshi by lazy {
        moshi.newBuilder()
            .add(SettingItemRawAdapter())
            .build()
    }

    private var accountOwner: Contact? = null
        private set

    private var isOwnerSet = false


    override fun setAccountOwner(owner: Contact?) {
        accountOwner = owner
    }

    private val syncScope = CoroutineScope(SupervisorJob() + dispatchers.io)

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

    // ===========================
    // Listener Management
    // ===========================
    private inner class SynchronizedListenerHolder {
        private val listeners: LinkedHashSet<DataSyncManagerListener> = LinkedHashSet()

        fun addListener(listener: DataSyncManagerListener): Boolean = synchronized(this) {
            listeners.add(listener)
        }

        fun removeListener(listener: DataSyncManagerListener): Boolean = synchronized(this) {
            listeners.remove(listener)
        }

        suspend fun <T> requestFromFirstListener(
            block: suspend (DataSyncManagerListener) -> T?
        ): T? {
            val listenersCopy = synchronized(this) {
                listeners.toList()
            }

            for (listener in listenersCopy) {
                try {
                    val result = block(listener)
                    if (result != null) {
                        return result
                    }
                } catch (e: CancellationException) {
                    throw e // Re-throw cancellation
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Continue to next listener on error
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

    private suspend fun <T> requestFromListener(
        block: suspend (DataSyncManagerListener) -> T?
    ): T? {
        return synchronizedListeners.requestFromFirstListener(block)
    }

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
        val feedStatus = FeedStatus(
            chatPubkey = chatPubkey,
            feedUrl = feedUrl,
            feedId = feedId,
            subscribed = subscribed,
            satsPerMinute = satsPerMinute,
            playerSpeed = playerSpeed,
            itemId = itemId
        )
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
        val feedItemStatus = FeedItemStatus(
            duration = duration,
            currentTime = currentTime
        )
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
        try {
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

            syncScope.launch {
                syncWithServer()
            }
        } catch (e: CancellationException) {
            println("saveDataSyncItem cancelled: ${e.message}")
            throw e
        } catch (e: Exception) {
            println("Error saving data sync item: ${e.message}")
            e.printStackTrace()
        }
    }

    private suspend fun encryptValue(value: String): String? {
        return try {
            requestFromListener { listener ->
                listener.onEncryptDataSync(value)
            }
        } catch (e: CancellationException) {
            println("encryptValue cancelled")
            null
        } catch (e: Exception) {
            println("Error encrypting value: ${e.message}")
            null
        }
    }

    private suspend fun decryptValue(value: String): String? {
        return try {
            requestFromListener { listener ->
                listener.onDecryptDataSync(value)
            }
        } catch (e: CancellationException) {
            println("decryptValue cancelled")
            null
        } catch (e: Exception) {
            println("Error decrypting value: ${e.message}")
            null
        }
    }

    override suspend fun syncWithServer() {
        // Don't try to acquire lock if already syncing
        if (!syncMutex.tryLock()) {
            println("Sync already in progress, skipping...")
            return
        }

        try {
            syncWithServerInternal()
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun syncWithServerInternal() {
        try {
            if (!syncScope.isActive) {
                println("Sync scope is not active, aborting sync")
                return
            }

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

            ensureActive()

            val serverDataString = getFileFromServer()

            if (serverDataString != null) {
                ensureActive()

                val decryptedData = decryptValue(serverDataString)

                if (decryptedData != null) {
                    try {
                        val serverItemsResponseRaw = decryptedData.toItemsResponseRaw(dataSyncMoshi)
                        val serverItems = serverItemsResponseRaw.toSettingItems()

                        ensureActive()

                        val mergedItems = mergeDataSyncItems(localItems, serverItems)

                        ensureActive()

                        applyMergedItemsToLocal(mergedItems, localItems)

                        ensureActive()

                        val uploadSuccess = uploadMergedDataToServer(mergedItems)

                        ensureActive()

                        if (uploadSuccess) {
                            clearDataSyncTable()
                        }

                    } catch (e: CancellationException) {
                        println("Sync cancelled during processing: ${e.message}")
                        throw e
                    } catch (e: Exception) {
                        e.printStackTrace()
                        _syncStatusStateFlow.value = SyncStatus.Error("Error parsing server data: ${e.message}")
                        return
                    }
                } else {
                    // No server data or decryption failed
                    if (localItems.isNotEmpty()) {
                        ensureActive()
                        val uploadSuccess = uploadMergedDataToServer(localItems)
                        if (uploadSuccess) {
                            ensureActive()
                            clearDataSyncTable()
                        }
                    }
                }
            } else {
                // No server data exists
                if (localItems.isNotEmpty()) {
                    ensureActive()
                    val uploadSuccess = uploadMergedDataToServer(localItems)
                    if (uploadSuccess) {
                        ensureActive()
                        clearDataSyncTable()
                    }
                }
            }

            _syncStatusStateFlow.value = SyncStatus.Success(System.currentTimeMillis())

        } catch (e: CancellationException) {
            println("Sync operation cancelled: ${e.message}")
            _syncStatusStateFlow.value = SyncStatus.Idle
        } catch (e: Exception) {
            e.printStackTrace()
            _syncStatusStateFlow.value = SyncStatus.Error(e.message ?: "Unknown error")
        }
    }

    private fun ensureActive() {
        if (!syncScope.isActive) {
            throw CancellationException("Sync scope is not active")
        }
    }

    private suspend fun clearDataSyncTable() {
        try {
            requestFromListener { listener ->
                listener.onClearDataSyncTable()
            }
        } catch (e: CancellationException) {
            println("clearDataSyncTable cancelled")
        } catch (e: Exception) {
            println("Error clearing data sync table: ${e.message}")
            e.printStackTrace()
        }
    }

    // ===========================
    // Helper Methods
    // ===========================
    private fun parseLocalValue(valueString: String, key: String): DataSyncJson {
        if (valueString.trim().startsWith("{")) {
            try {
                val map = parseSimpleJsonObject(valueString)
                return DataSyncJson.ObjectValue(map)
            } catch (e: Exception) {
                // Fall through to StringValue
            }
        }
        return DataSyncJson.StringValue(valueString)
    }

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

        // Add all server items first
        serverItems.forEach { serverItem ->
            val key = "${serverItem.key}:${serverItem.identifier}"
            mergedMap[key] = serverItem
        }

        // Process local items
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
            try {
                ensureActive()

                val key = "${mergedItem.key}:${mergedItem.identifier}"
                val localItem = localMap[key]

                if (localItem == null) {
                    // Item doesn't exist locally - add it
                    saveDataSyncItemToDb(mergedItem)
                    applyItemToActualTables(mergedItem)
                } else {
                    // Item exists locally - check if server version is newer
                    val localDate = parseDate(localItem.date)
                    val mergedDate = parseDate(mergedItem.date)

                    if (mergedDate > localDate) {
                        // Server version is newer - update local DB and actual tables
                        saveDataSyncItemToDb(mergedItem)
                        applyItemToActualTables(mergedItem)
                    }
                }
            } catch (e: CancellationException) {
                println("applyMergedItemsToLocal cancelled")
                throw e
            } catch (e: Exception) {
                println("Error applying item to local: ${e.message}")
                e.printStackTrace()
                // Continue with next item
            }
        }
    }

    private suspend fun applyItemToActualTables(item: SettingItem) {
        try {
            val valueString = when (val value = item.value) {
                is DataSyncJson.StringValue -> value.value
                is DataSyncJson.ObjectValue -> {
                    value.value.entries.joinToString(
                        prefix = "{",
                        postfix = "}",
                        separator = ","
                    ) { (k, v) -> "\"$k\":\"$v\"" }
                }
            }

            requestFromListener { listener ->
                listener.onApplySyncedData(
                    key = item.key,
                    identifier = item.identifier,
                    value = valueString
                )
            }
        } catch (e: CancellationException) {
            println("applyItemToActualTables cancelled")
        } catch (e: Exception) {
            println("Error applying item to actual tables: ${e.message}")
            e.printStackTrace()
        }
    }

    private suspend fun saveDataSyncItemToDb(item: SettingItem) {
        try {
            val valueString = when (val value = item.value) {
                is DataSyncJson.StringValue -> value.value
                is DataSyncJson.ObjectValue -> {
                    value.value.entries.joinToString(
                        prefix = "{",
                        postfix = "}",
                        separator = ","
                    ) { (k, v) -> "\"$k\":\"$v\"" }
                }
            }

            requestFromListener { listener ->
                listener.onSaveDataSyncItem(
                    key = item.key,
                    identifier = item.identifier,
                    value = valueString,
                    timestamp = convertSecondsToMilliseconds(item.date)
                )
            }
        } catch (e: CancellationException) {
            println("saveDataSyncItemToDb cancelled")
        } catch (e: Exception) {
            println("Error saving data sync item to DB: ${e.message}")
            e.printStackTrace()
        }
    }

    private suspend fun uploadMergedDataToServer(mergedItems: List<SettingItem>): Boolean {
        return try {
            ensureActive()

            val token = getAuthenticationToken() ?: return false
            val ownerPubKey = accountOwner?.nodePubKey?.value ?: return false

            val pubkeyBase64Url = hexToBase64URL(ownerPubKey)

            val itemsResponseRaw = mergedItems.toItemsResponseRaw()

            val adapter = dataSyncMoshi.adapter(ItemsResponseRaw::class.java)
            val jsonData = adapter.toJson(itemsResponseRaw)

            val encryptedData = encryptValue(jsonData) ?: return false

            ensureActive()

            var uploadSuccess = false
            var uploadCompleted = false


            withTimeoutOrNull(30000L) {
                networkQueryMemeServer.uploadDataSyncFile(
                    authenticationToken = token,
                    memeServerHost = MediaHost.DEFAULT,
                    pubkey = pubkeyBase64Url,
                    data = encryptedData
                ).collect { loadResponse ->
                    when (loadResponse) {
                        is LoadResponse.Loading -> {
                        }
                        is Response.Error -> {
                            uploadSuccess = false
                            uploadCompleted = true
                        }
                        is Response.Success -> {
                            uploadSuccess = true
                            uploadCompleted = true
                        }
                    }

                    if (uploadCompleted) {
                        ensureActive()
                    }
                }
            } ?: run {
                println("Upload timed out")
                return false
            }

            uploadSuccess
        } catch (e: CancellationException) {
            println("Upload cancelled: ${e.message}")
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ===========================
    // Server Communication
    // ===========================
    private suspend fun getFileFromServer(): String? {
        return try {
            ensureActive()

            val token = getAuthenticationToken() ?: return null

            var encryptedString: String? = null
            var fetchCompleted = false

            // Use timeout to prevent hanging
            withTimeoutOrNull(30000L) { // 30 second timeout
                networkQueryMemeServer.getDataSyncFile(
                    authenticationToken = token,
                    memeServerHost = MediaHost.DEFAULT
                ).collect { loadResponse ->
                    when (loadResponse) {
                        is LoadResponse.Loading -> {}
                        is Response.Error -> {
                            println("Error fetching data sync: ${loadResponse.message}")
                            fetchCompleted = true
                        }
                        is Response.Success -> {
                            encryptedString = loadResponse.value
                            fetchCompleted = true
                        }
                    }

                    if (fetchCompleted) {
                        ensureActive()
                    }
                }
            } ?: run {
                println("Fetch timed out")
                return null
            }

            encryptedString
        } catch (e: CancellationException) {
            println("Fetch cancelled: ${e.message}")
            null
        } catch (e: Exception) {
            println("Exception fetching data sync: ${e.message}")
            null
        }
    }

    // ===========================
    // Utility Methods
    // ===========================
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

    private fun getTimestampInMilliseconds(): Long =
        System.currentTimeMillis()

    private suspend fun getAuthenticationToken(): AuthenticationToken? {
        return try {
            val memeServerHost = MediaHost.DEFAULT
            val token = memeServerTokenHandler.retrieveAuthenticationToken(memeServerHost)
                ?: return null

            token
        } catch (e: CancellationException) {
            println("getAuthenticationToken cancelled")
            null
        } catch (e: Exception) {
            println("Exception retrieving authentication token: ${e.message}")
            null
        }
    }

    private fun hexToBase64URL(hex: String): String {
        val bytes = mutableListOf<Byte>()
        var hexString = hex

        // Remove any spaces or special characters
        hexString = hexString.replace(Regex("[^0-9A-Fa-f]"), "")

        // Convert hex string to bytes
        var i = 0
        while (i < hexString.length) {
            val byteString = hexString.substring(i, minOf(i + 2, hexString.length))
            bytes.add(byteString.toInt(16).toByte())
            i += 2
        }

        val byteArray = bytes.toByteArray()

        // Convert to base64
        val base64 = Base64.encodeToString(
            byteArray,
            Base64.NO_WRAP
        )

        // Convert to base64URL format
        return base64
            .replace("+", "-")
            .replace("/", "_")
            .replace("=", "")
    }

    // Cleanup method to cancel ongoing operations
    fun cleanup() {
        syncScope.cancel()
    }
}