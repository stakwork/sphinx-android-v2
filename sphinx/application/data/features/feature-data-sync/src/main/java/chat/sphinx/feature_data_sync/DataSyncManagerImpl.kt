package chat.sphinx.feature_data_sync

import chat.sphinx.concept_data_sync.DataSyncManager
import chat.sphinx.concept_data_sync.DataSyncManagerListener
import chat.sphinx.concept_data_sync.model.SyncStatus
import chat.sphinx.concept_repository_data_sync.DataSyncRepository
import chat.sphinx.wrapper_common.DateTime
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
import com.squareup.moshi.Moshi
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet

class DataSyncManagerImpl(
    private val moshi: Moshi,
    private val dispatchers: CoroutineDispatchers,
) : DataSyncManager() {

    private var dataSyncRepository: DataSyncRepository? = null

    fun setRepository(repository: DataSyncRepository) {
        this.dataSyncRepository = repository
    }

    private val syncMutex = Mutex()
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

    override fun addListener(listener: DataSyncManagerListener): Boolean =
        listeners.add(listener)

    override fun removeListener(listener: DataSyncManagerListener): Boolean =
        listeners.remove(listener)

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
        dataSyncRepository?.upsertDataSync(
            key = DataSyncKey(key),
            identifier = DataSyncIdentifier(identifier),
            date = DateTime(Date()),
            value = DataSyncValue(value)
        )

        syncWithServer()
    }
    override suspend fun syncWithServer() = syncMutex.withLock {
        withContext(dispatchers.io) {
            try {
                _syncStatusStateFlow.value = SyncStatus.Syncing

                val serverDataString = getFileFromServer() ?: run {
                    _syncStatusStateFlow.value = SyncStatus.Error("Failed to fetch server file")
                    return@withContext
                }

                val itemsResponse = serverDataString.toItemsResponse(moshi)
                val dbItems = dataSyncRepository?.getAllDataSync?.firstOrNull() ?: emptyList()

                // Find items that exist on server but not locally
                val missingItems = findMissingItems(dbItems, itemsResponse.items)

                // Notify listeners of remote changes
                for (item in missingItems) {
                    notifyListenersOfRemoteChange(item)
                }

                // Merge local and server items
                val updatedItems = itemsResponse.items.toMutableList()

//                for (dbItem in dbItems) {
//                    val serverItemIndex = updatedItems.indexOfFirst {
//                        it.key == dbItem.key && it.identifier == dbItem.identifier
//                    }
//
//                    if (serverItemIndex != -1) {
//                        val serverItem = updatedItems[serverItemIndex]
//
//                        if (serverItem.dateTime.time < dbItem.date.time) {
//                            // Local is newer - update server
//                            val jsonValue = JsonValue.fromString(dbItem.value, dbItem.key)
//                            if (jsonValue != null) {
//                                updatedItems[serverItemIndex] = SettingItem(
//                                    key = dbItem.key,
//                                    identifier = dbItem.identifier,
//                                    date = (dbItem.date.time / 1000.0).toString(),
//                                    value = jsonValue
//                                )
//                            }
//                        } else {
//                            // Server is newer - notify listeners
//                            notifyListenersOfRemoteChange(serverItem)
//                        }
//                    } else {
//                        // Doesn't exist on server - add it
//                        val jsonValue = JsonValue.fromString(dbItem.value, dbItem.key)
//                        if (jsonValue != null) {
//                            updatedItems.add(
//                                SettingItem(
//                                key = dbItem.key,
//                                identifier = dbItem.identifier,
//                                date = (dbItem.date.time / 1000.0).toString(),
//                                value = jsonValue
//                            )
//                            )
//                        }
//                    }
//
//                    // Delete local item after processing
//                    dataSyncRepository.deleteDataSync(dbItem.key, dbItem.identifier)
//                }

                // Save updated file to server
//                saveFileToServer(ItemsResponse(updatedItems))

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

    // File operations (plain text - no encryption)
    private fun getFileFromServer(): String? {
        // TODO: Implement actual server fetch with encryption/decryption
//        val file = File(context.filesDir, "datasync.txt")
//
//        if (!file.exists()) {
//            val defaultContent = createDefaultFile()
//            file.writeText(defaultContent)
//            return defaultContent
//        }
//
//        return try {
//            file.readText()
//        } catch (e: Exception) {
//            null
//        }
        return null
    }

    private fun saveFileToServer(itemsResponse: ItemsResponse) {
//        val jsonString = itemsResponse.toOriginalFormatJson(moshi) ?: return
//
//        // TODO: Implement encryption here
//        // val encrypted = yourEncryptionMethod(jsonString)
//
//        val file = File(context.filesDir, "datasync.txt")
//        file.writeText(jsonString) // Currently plain text
//
//        // TODO: Upload encrypted content to memes server
        // networkClient.uploadDataSyncFile(encrypted)
    }

    private fun createDefaultFile(): String {
        val timestamp = (System.currentTimeMillis() / 1000.0) - 3600
        return """{"items": [{"key": "tip_amount", "identifier": "0", "date": "$timestamp", "value": "12"}]}"""
    }
}