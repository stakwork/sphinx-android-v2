package chat.sphinx.concept_data_sync

import chat.sphinx.concept_data_sync.model.SyncStatus
import chat.sphinx.wrapper_common.datasync.DataSync
import chat.sphinx.wrapper_contact.Contact
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

    abstract suspend fun saveTimezoneForChat(
        chatPubkey: String,
        timezoneEnabled: Boolean,
        timezoneIdentifier: String
    )

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

    abstract suspend fun syncWithServer(pendingDataSync: DataSync? = null)

    // Listener methods
    abstract fun addListener(listener: DataSyncManagerListener): Boolean
    abstract fun removeListener(listener: DataSyncManagerListener): Boolean
    abstract fun setAccountOwner(owner: Contact?)

}

interface DataSyncManagerListener {

    // Called when manager needs to save data locally
    fun onSaveDataSyncItem(
        key: String,
        identifier: String,
        value: String,
        timestamp: Long
    )

    suspend fun onApplySyncedData(
        key: String,
        identifier: String,
        value: String
    )

    suspend fun onEncryptDataSync(value: String): String?
    suspend fun onDecryptDataSync(value: String): String?
    fun onClearDataSyncTable()
}