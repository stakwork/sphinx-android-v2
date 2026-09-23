package chat.sphinx.example.wrapper_mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MixerErrorMapperTest {

    @Test
    fun allowlistedCodesMapToDistinctKinds() {
        assertEquals(
            MixerErrorMessageKind.CLN_UNAVAILABLE,
            mixerErrorMessageKind(MIXER_ERROR_CLN_UNAVAILABLE)
        )
        assertEquals(
            MixerErrorMessageKind.CLN_TIMEOUT,
            mixerErrorMessageKind(MIXER_ERROR_CLN_TIMEOUT)
        )
        assertEquals(
            MixerErrorMessageKind.INSUFFICIENT_BALANCE,
            mixerErrorMessageKind(MIXER_ERROR_INSUFFICIENT_BALANCE)
        )
    }

    @Test
    fun unknownAndUnrecognizedFallBackToGeneric() {
        assertEquals(MixerErrorMessageKind.GENERIC, mixerErrorMessageKind(MIXER_ERROR_UNKNOWN))
        assertEquals(MixerErrorMessageKind.GENERIC, mixerErrorMessageKind("mixer is down"))
        assertEquals(MixerErrorMessageKind.GENERIC, mixerErrorMessageKind("reason: cln timeout"))
        assertEquals(MixerErrorMessageKind.GENERIC, mixerErrorMessageKind(null))
        assertEquals(MixerErrorMessageKind.GENERIC, mixerErrorMessageKind(""))
    }

    @Test
    fun mapperNeverReturnsRawCodeOrReason() {
        val samples = listOf(
            MIXER_ERROR_CLN_UNAVAILABLE,
            MIXER_ERROR_CLN_TIMEOUT,
            MIXER_ERROR_INSUFFICIENT_BALANCE,
            MIXER_ERROR_UNKNOWN,
            "raw reason from mixer",
            "CLN_UNAVAILABLE extra"
        )
        samples.forEach { raw ->
            val kind = mixerErrorMessageKind(raw)
            assertNotEquals(raw, kind.name.takeIf { raw != kind.name && raw.startsWith("CLN") })
            assertFalseContainsReason(kind, raw)
        }
    }

    @Test
    fun eventIdDoesNotChangeMappedString() {
        val first = ConnectManagerError.MixerOperationError(MIXER_ERROR_CLN_TIMEOUT, 1L)
        val second = ConnectManagerError.MixerOperationError(MIXER_ERROR_CLN_TIMEOUT, 2L)
        assertNotEquals(first, second)
        assertEquals(mixerErrorMessageKind(first), mixerErrorMessageKind(second))
        assertEquals(MixerErrorMessageKind.CLN_TIMEOUT, mixerErrorMessageKind(first))
    }

    private fun assertFalseContainsReason(kind: MixerErrorMessageKind, raw: String) {
        val rendered = kind.name
        if (raw != MIXER_ERROR_CLN_UNAVAILABLE &&
            raw != MIXER_ERROR_CLN_TIMEOUT &&
            raw != MIXER_ERROR_INSUFFICIENT_BALANCE
        ) {
            org.junit.Assert.assertFalse(rendered == raw)
            org.junit.Assert.assertEquals(MixerErrorMessageKind.GENERIC, kind)
        }
    }
}
