package chat.sphinx.concept_data_sync

import chat.sphinx.concept_data_sync.model.DataSyncError
import chat.sphinx.concept_data_sync.model.DataSyncState
import chat.sphinx.concept_data_sync.model.SyncStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * The DataSyncManager abstract class manages synchronization of user preferences
 * and settings across multiple devices through an encrypted server file.
 */

abstract class DataSyncManager {

    abstract val syncStatusStateFlow: StateFlow<SyncStatus>

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
    abstract suspend fun saveFeedItemStatus(feedId: String, itemId: String, duration: Int, currentTime: Int)

    // Sync trigger - called after saves or on app init
    abstract suspend fun syncWithServer()

    // Listener methods
    abstract fun addListener(listener: DataSyncManagerListener): Boolean
    abstract fun removeListener(listener: DataSyncManagerListener): Boolean
}

interface DataSyncManagerListener {
    // Called when server has newer data
    suspend fun onRemoteTipAmountChanged(tipAmount: Long)
    suspend fun onRemotePrivatePhotoChanged(isPrivate: Boolean)
    suspend fun onRemoteTimezoneChanged(chatPubkey: String, timezoneEnabled: Boolean, timezoneIdentifier: String)
    suspend fun onRemoteFeedStatusChanged(
        feedId: String,
        chatPubkey: String,
        feedUrl: String,
        subscribed: Boolean,
        satsPerMinute: Int,
        playerSpeed: Double,
        itemId: String
    )
    suspend fun onRemoteFeedItemStatusChanged(feedId: String, itemId: String, duration: Int, currentTime: Int)
}