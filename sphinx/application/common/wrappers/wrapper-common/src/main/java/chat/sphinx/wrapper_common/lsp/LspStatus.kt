package chat.sphinx.wrapper_common.lsp

@Suppress("NOTHING_TO_INLINE")
inline fun LspStatus.isActive(): Boolean =
    this is LspStatus.Active

@Suppress("NOTHING_TO_INLINE")
inline fun LspStatus.isExpired(): Boolean =
    this is LspStatus.Expired

@Suppress("NOTHING_TO_INLINE")
inline fun Int?.toLspStatus(): LspStatus =
    when (this) {
        null,
        LspStatus.ACTIVE -> {
            LspStatus.Active
        }
        LspStatus.EXPIRED -> {
            LspStatus.Expired
        }
        else -> {
            LspStatus.Unknown(this)
        }
    }

sealed class LspStatus {

    companion object {
        const val EXPIRED = 0
        const val ACTIVE = 1
    }

    abstract val value: Int

    object Expired: LspStatus() {
        override val value: Int
            get() = EXPIRED
    }

    object Active: LspStatus() {
        override val value: Int
            get() = ACTIVE
    }

    data class Unknown(override val value: Int): LspStatus()
}