package chat.sphinx.wrapper_common.datasync

inline val DataSyncKey.isTipAmount: Boolean
    get() = this is DataSyncKey.TipAmount

inline val DataSyncKey.isPrivatePhoto: Boolean
    get() = this is DataSyncKey.PrivatePhoto

inline val DataSyncKey.isTimezone: Boolean
    get() = this is DataSyncKey.Timezone

inline val DataSyncKey.isFeedStatus: Boolean
    get() = this is DataSyncKey.FeedStatus

inline val DataSyncKey.isFeedItemStatus: Boolean
    get() = this is DataSyncKey.FeedItemStatus

inline val DataSyncKey.isUnknown: Boolean
    get() = this is DataSyncKey.Unknown

@Suppress("NOTHING_TO_INLINE")
inline fun String.toDataSyncKey(): DataSyncKey =
    when (this) {
        DataSyncKey.TIP_AMOUNT -> DataSyncKey.TipAmount
        DataSyncKey.PRIVATE_PHOTO -> DataSyncKey.PrivatePhoto
        DataSyncKey.TIMEZONE -> DataSyncKey.Timezone
        DataSyncKey.FEED_STATUS -> DataSyncKey.FeedStatus
        DataSyncKey.FEED_ITEM_STATUS -> DataSyncKey.FeedItemStatus
        else -> DataSyncKey.Unknown(this)
    }

sealed class DataSyncKey {

    companion object {
        const val TIP_AMOUNT = "tip_amount"
        const val PRIVATE_PHOTO = "private_photo"
        const val TIMEZONE = "timezone"
        const val FEED_STATUS = "feed_status"
        const val FEED_ITEM_STATUS = "feed_item_status"
    }

    abstract val value: String

    object TipAmount : DataSyncKey() {
        override val value: String
            get() = TIP_AMOUNT
    }

    object PrivatePhoto : DataSyncKey() {
        override val value: String
            get() = PRIVATE_PHOTO
    }

    object Timezone : DataSyncKey() {
        override val value: String
            get() = TIMEZONE
    }

    object FeedStatus : DataSyncKey() {
        override val value: String
            get() = FEED_STATUS
    }

    object FeedItemStatus : DataSyncKey() {
        override val value: String
            get() = FEED_ITEM_STATUS
    }

    data class Unknown(override val value: String) : DataSyncKey()
}