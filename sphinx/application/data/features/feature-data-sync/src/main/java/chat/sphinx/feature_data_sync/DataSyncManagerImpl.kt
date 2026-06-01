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
import chat.sphinx.wrapper_common.toDateTime
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

    // Debouncing support for sync operations
    private var pendingSyncJob: Job? = null
    private var pendingDataSyncItems = mutableListOf<DataSync>()
    private val pendingItemsLock = Mutex()
    private val syncDebounceDelayMs = 500L

    // Retry configuration for network operations
    private val maxRetries = 3
    private val retryDelayMs = 1000L

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
            val timestamp = getTimestampInMilliseconds()

            withContext(dispatchers.io) {
                requestFromListener { listener ->
                    listener.onSaveDataSyncItem(
                        key = key,
                        identifier = identifier,
                        value = value,
                        timestamp = timestamp
                    )
                }
            }

            val pendingDataSync = DataSync(
                sync_key = key.toDataSyncKey(),
                identifier = DataSyncIdentifier(identifier),
                date = timestamp.toDateTime(),
                sync_value = DataSyncValue(value)
            )

            // Use debounced sync to batch rapid changes
            scheduleDebouncedSync(pendingDataSync)
        } catch (e: CancellationException) {
            println("saveDataSyncItem cancelled: ${e.message}")
            throw e
        } catch (e: Exception) {
            println("Error saving data sync item: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun scheduleDebouncedSync(pendingDataSync: DataSync) {
        syncScope.launch {
            pendingItemsLock.withLock {
                // Remove any existing item with same key/identifier
                pendingDataSyncItems.removeAll { existing ->
                    existing.sync_key == pendingDataSync.sync_key &&
                            existing.identifier == pendingDataSync.identifier
                }
                pendingDataSyncItems.add(pendingDataSync)
            }

            // Cancel previous pending sync job
            pendingSyncJob?.cancel()

            // Schedule new debounced sync
            pendingSyncJob = syncScope.launch {
                delay(syncDebounceDelayMs)

                val itemsToSync = pendingItemsLock.withLock {
                    val items = pendingDataSyncItems.toList()
                    pendingDataSyncItems.clear()
                    items
                }

                // Sync with the most recent item (they're all batched in localDataSync anyway)
                if (itemsToSync.isNotEmpty()) {
                    syncWithServer(itemsToSync.last())
                }
            }
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

    override suspend fun syncWithServer(pendingDataSync: DataSync?) {
        // Don't try to acquire lock if already syncing
        if (!syncMutex.tryLock()) {
            println("Sync already in progress, skipping...")
            return
        }

        try {
            syncWithServerInternal(pendingDataSync)
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun syncWithServerInternal(pendingDataSync: DataSync?) {
        try {
            if (!syncScope.isActive) {
                println("Sync scope is not active, aborting sync")
                return
            }

            _syncStatusStateFlow.value = SyncStatus.Syncing

            val localDataSync = _dataSyncStateFlow.value.toMutableList()

            pendingDataSync?.let { pending ->
                val exists = localDataSync.any { existing ->
                    existing.sync_key == pending.sync_key &&
                            existing.identifier == pending.identifier
                }
                if (!exists) {
                    localDataSync.add(pending)
                }
            }

            val localItems = localDataSync.map { dataSync ->
                SettingItem(
                    key = dataSync.sync_key.value,
                    identifier = dataSync.identifier.value,
                    date = convertTimestampToSeconds(dataSync.date.time),
                    value = parseLocalValue(dataSync.sync_value.value)
                )
            }

            ensureActive()

            val serverDataString = getFileFromServer()

            if (serverDataString != null) {
                ensureActive()

                // Validate server data is not empty or malformed
                if (serverDataString.isBlank()) {
                    println("DataSync: Server returned empty data, treating as no data")
                } else {
                    val decryptedData = decryptValue(serverDataString)

                    if (decryptedData != null && isValidJsonStructure(decryptedData)) {
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
                        // Decryption failed or invalid JSON structure
                        println("DataSync: Failed to decrypt or invalid data structure, uploading local data")
                        if (localItems.isNotEmpty()) {
                            ensureActive()
                            val uploadSuccess = uploadMergedDataToServer(localItems)
                            if (uploadSuccess) {
                                ensureActive()
                                clearDataSyncTable()
                            }
                        }
                    }
                }
            } else {
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
    private fun parseLocalValue(valueString: String): DataSyncJson {
        if (valueString.trim().startsWith("{")) {
            try {
                // Uses shared parseSimpleJsonObject from DataSyncExtensions
                val map = parseSimpleJsonObject(valueString)
                return DataSyncJson.ObjectValue(map)
            } catch (e: Exception) {
                // Fall through to StringValue
            }
        }
        return DataSyncJson.StringValue(valueString)
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

            // Use retry for network resilience
            val uploadResult = withRetry("Upload") {
                val result = withTimeoutOrNull(30000L) {
                    networkQueryMemeServer.uploadDataSyncFile(
                        authenticationToken = token,
                        memeServerHost = MediaHost.DEFAULT,
                        pubkey = pubkeyBase64Url,
                        data = encryptedData
                    ).filterNot { it is LoadResponse.Loading }
                        .first()
                }

                when (result) {
                    is Response.Success -> true
                    is Response.Error -> {
                        println("Upload attempt failed: ${result.message}")
                        null  // Return null to trigger retry
                    }
                    else -> null
                }
            }

            if (uploadResult == true) {
                true
            } else {
                _syncStatusStateFlow.value = SyncStatus.Error("Upload failed after retries")
                false
            }
        } catch (e: CancellationException) {
            println("Upload cancelled: ${e.message}")
            false
        } catch (e: Exception) {
            e.printStackTrace()
            _syncStatusStateFlow.value = SyncStatus.Error("Upload error: ${e.message}")
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

            // Use retry for network resilience
            withRetry("Fetch") {
                val result = withTimeoutOrNull(30000L) {
                    networkQueryMemeServer.getDataSyncFile(
                        authenticationToken = token,
                        memeServerHost = MediaHost.DEFAULT
                    ).filterNot { it is LoadResponse.Loading }
                        .first()
                }

                when (result) {
                    is Response.Success -> result.value
                    is Response.Error -> {
                        println("Fetch attempt failed: ${result.message}")
                        null  // Return null to trigger retry
                    }
                    else -> null
                }
            }
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

    /**
     * Retry a suspend operation with exponential backoff.
     * Returns the result on success, or null if all retries fail.
     */
    private suspend fun <T> withRetry(
        operationName: String,
        block: suspend () -> T?
    ): T? {
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                ensureActive()
                val result = block()
                if (result != null) {
                    return result
                }
            } catch (e: CancellationException) {
                throw e  // Don't retry on cancellation
            } catch (e: Exception) {
                lastException = e
                println("$operationName attempt ${attempt + 1} failed: ${e.message}")

                if (attempt < maxRetries - 1) {
                    val delayTime = retryDelayMs * (attempt + 1)  // Linear backoff
                    delay(delayTime)
                }
            }
        }

        println("$operationName failed after $maxRetries attempts: ${lastException?.message}")
        return null
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

    private fun getTimestampInMilliseconds(): Long =
        System.currentTimeMillis()

    /**
     * Validate that a string appears to be valid JSON before attempting to parse.
     * This prevents unnecessary parsing attempts on corrupted or incomplete data.
     */
    private fun isValidJsonStructure(json: String): Boolean {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return false

        // Basic structural validation for JSON object
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
               (trimmed.startsWith("[") && trimmed.endsWith("]"))
    }

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

    // Cleanup method to cancel ongoing operations and release resources
    fun cleanup() {
        // Cancel any pending debounced sync
        pendingSyncJob?.cancel()
        pendingSyncJob = null

        // Clear pending items
        syncScope.launch {
            pendingItemsLock.withLock {
                pendingDataSyncItems.clear()
            }
        }

        // Reset sync status
        _syncStatusStateFlow.value = SyncStatus.Idle

        // Cancel all ongoing operations
        syncScope.cancel()
    }
}