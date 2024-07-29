package chat.sphinx.wrapper_common.lsp

@Suppress("NOTHING_TO_INLINE")
inline fun LsatStatus.isActive(): Boolean =
    this is LsatStatus.Active

@Suppress("NOTHING_TO_INLINE")
inline fun LsatStatus.isExpired(): Boolean =
    this is LsatStatus.Expired

@Suppress("NOTHING_TO_INLINE")
inline fun Int?.toLsatStatus(): LsatStatus =
    when (this) {
        null,
        LsatStatus.ACTIVE -> {
            LsatStatus.Active
        }
        LsatStatus.EXPIRED -> {
            LsatStatus.Expired
        }
        else -> {
            LsatStatus.Unknown(this)
        }
    }

sealed class LsatStatus {

    companion object {
        const val EXPIRED = 0
        const val ACTIVE = 1
    }

    abstract val value: Int

    object Expired: LsatStatus() {
        override val value: Int
            get() = EXPIRED
    }

    object Active: LsatStatus() {
        override val value: Int
            get() = ACTIVE
    }

    data class Unknown(override val value: Int): LsatStatus()
}