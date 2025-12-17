package chat.sphinx.wrapper_chat

@Suppress("NOTHING_TO_INLINE")
inline fun OwnedTribe.isTrue(): Boolean =
    this is OwnedTribe.True

@Suppress("NOTHING_TO_INLINE")
inline fun Int.toOwnedTribe(): OwnedTribe =
    when (this) {
        OwnedTribe.YES -> {
            OwnedTribe.True
        }
        else -> {
            OwnedTribe.False
        }
    }

@Suppress("NOTHING_TO_INLINE")
inline fun Boolean.toOwnedTribe(): OwnedTribe =
    if (this) OwnedTribe.True else OwnedTribe.False

sealed class OwnedTribe {

    companion object {
        const val YES = 1
        const val NO = 0
    }

    abstract val value: Int

    object True: OwnedTribe() {
        override val value: Int
            get() = YES
    }

    object False: OwnedTribe() {
        override val value: Int
            get() = NO
    }
}
