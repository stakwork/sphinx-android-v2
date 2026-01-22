package chat.sphinx.concept_data_sync

import chat.sphinx.concept_data_sync.model.DataSyncError
import chat.sphinx.concept_data_sync.model.DataSyncState
import chat.sphinx.concept_data_sync.model.SyncStatus
import chat.sphinx.wrapper_common.datasync.DataSync
import kotlinx.coroutines.flow.StateFlow

/**
 * The DataSyncManager abstract class manages synchronization of user preferences
 * and settings across multiple devices through an encrypted server file.
 */

abstract class DataSyncManager {

    abstract val dataSyncStateFlow: StateFlow<List<DataSync>>
    abstract val syncStatusStateFlow: StateFlow<SyncStatus>

    abstract fun updateDataSyncList(dataSyncList: List<DataSync>)

    // Save methods - called when local data changes
    abstract suspend fun saveTipAmount(value: String)
    abstract suspend fun savePrivatePhoto(value: String)
    abstract suspend fun saveTimezoneForChat(chatPubkey: String, timezoneEnabled: Boolean, timezoneIdentifier: String)
    abstract suspend fun saveFeedStatus(
        feedId: String,
        chatPubkey: String,
        feedUrl: String,
        subscribed: Boolean,
        satsPerMinute: Int,
        playerSpeed: Double,
        itemId: String
    )

    abstract suspend fun saveFeedItemStatus(
        feedId: String,
        itemId: String,
        duration: Int,
        currentTime: Int
    )
    // Sync trigger - called after saves or on app init
    abstract suspend fun syncWithServer()

    // Listener methods
    abstract fun addListener(listener: DataSyncManagerListener): Boolean
    abstract fun removeListener(listener: DataSyncManagerListener): Boolean
}

interface DataSyncManagerListener {
    // Called when server has newer data that should be applied locally
    fun onRemoteTipAmountChanged(tipAmount: Long)
    fun onRemotePrivatePhotoChanged(isPrivate: Boolean)
    fun onRemoteTimezoneChanged(chatPubkey: String, timezoneEnabled: Boolean, timezoneIdentifier: String)
    fun onRemoteFeedStatusChanged(
        feedId: String,
        chatPubkey: String,
        feedUrl: String,
        subscribed: Boolean,
        satsPerMinute: Int,
        playerSpeed: Double,
        itemId: String
    )
    fun onRemoteFeedItemStatusChanged(feedId: String, itemId: String, duration: Int, currentTime: Int)

    // Called when manager needs to save data locally
    fun onSaveDataSyncItem(key: String, identifier: String, value: String, timestamp: Long)

    suspend fun onEncryptDataSync(value: String): String?
    suspend fun onDecryptDataSync(value: String): String?

}