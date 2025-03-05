package chat.sphinx.wrapper_message

@Suppress("NOTHING_TO_INLINE")
inline fun TimezoneEnabled.isTrue(): Boolean =
    this is TimezoneEnabled.True

@Suppress("NOTHING_TO_INLINE")
inline fun Int.toTimezoneEnabled(): TimezoneEnabled =
    when (this) {
        TimezoneEnabled.ENABLED -> {
            TimezoneEnabled.True
        }
        else -> {
            TimezoneEnabled.False
        }
    }

@Suppress("NOTHING_TO_INLINE")
inline fun Boolean.toTimezoneEnabled(): TimezoneEnabled =
    if (this) TimezoneEnabled.True else TimezoneEnabled.False

sealed class TimezoneEnabled {

    companion object {
        const val ENABLED = 1
        const val DISABLED = 0
    }

    abstract val value: Int

    object True: TimezoneEnabled() {
        override val value: Int
            get() = ENABLED
    }

    object False: TimezoneEnabled() {
        override val value: Int
            get() = DISABLED
    }
}
