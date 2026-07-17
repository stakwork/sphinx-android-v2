package chat.sphinx.feature_network_query_hive

import chat.sphinx.test_network_query.NetworkQueryTestHelper
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test

class NetworkQueryHiveImplUnitTest : NetworkQueryTestHelper() {

    @Test
    fun `test stub`() =
        testDispatcher.runBlockingTest {
            // NetworkQueryHiveImpl uses NetworkCall (not NetworkRelayCall)
            // Hive is an external API and must never receive relay Authorization headers.
        }
}
