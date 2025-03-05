package chat.sphinx.wrapper_message

@Suppress("NOTHING_TO_INLINE")
inline fun TimezoneUpdated.isTrue(): Boolean =
    this is TimezoneUpdated.True

@Suppress("NOTHING_TO_INLINE")
inline fun Int.toTimezoneUpdated(): TimezoneUpdated =
    when (this) {
        TimezoneUpdated.UPDATED -> {
            TimezoneUpdated.True
        }
        else -> {
            TimezoneUpdated.False
        }
    }

@Suppress("NOTHING_TO_INLINE")
inline fun Boolean.toTimezoneUpdated(): TimezoneUpdated =
    if (this) TimezoneUpdated.True else TimezoneUpdated.False

sealed class TimezoneUpdated {

    companion object {
        const val UPDATED = 1
        const val NOT_UPDATED = 0
    }

    abstract val value: Int

    object True: TimezoneUpdated() {
        override val value: Int
            get() = UPDATED
    }

    object False: TimezoneUpdated() {
        override val value: Int
            get() = NOT_UPDATED
    }
}
