package chat.sphinx.feature_network_query_hive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NetworkQueryHiveImplTest {

    @Test
    fun `encodePathSegment percent-encodes spaces as percent-20 not plus`() {
        val encoded = NetworkQueryHiveImpl.encodePathSegment("hello world")
        assertEquals("hello%20world", encoded)
        assertFalse(encoded.contains("+"))
    }

    @Test
    fun `encodePathSegment percent-encodes slashes and plus signs`() {
        assertEquals("a%2Fb", NetworkQueryHiveImpl.encodePathSegment("a/b"))
        assertEquals("plus%2Bsign", NetworkQueryHiveImpl.encodePathSegment("plus+sign"))
    }
}
