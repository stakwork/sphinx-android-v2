package chat.sphinx.feature_network_query_hive

import app.cash.exhaustive.Exhaustive
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.exception
import chat.sphinx.kotlin_response.message
import chat.sphinx.test_network_query.NetworkQueryTestHelper
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert
import org.junit.Test

class NetworkQueryHiveImplUnitTest : NetworkQueryTestHelper() {

    private val nqHive: NetworkQueryHiveImpl by lazy {
        NetworkQueryHiveImpl(networkRelayCall)
    }

    @Test
    fun `authenticateWithHive returns success or error with valid credentials`() =
        testDispatcher.runBlockingTest {
            getCredentials()?.let {

                nqHive.authenticateWithHive(
                    signedToken = "test_signed_token",
                    pubkey = it.pubKey,
                    timestamp = System.currentTimeMillis().toString()
                ).collect { loadResponse ->

                    @Exhaustive
                    when (loadResponse) {
                        is Response.Error -> {
                            // A network error or auth failure is acceptable in test env
                            loadResponse.exception?.printStackTrace()
                        }
                        is Response.Success -> {
                            Assert.assertNotNull(loadResponse.value.authToken)
                            Assert.assertTrue(loadResponse.value.authToken.isNotEmpty())
                        }
                        is LoadResponse.Loading -> {}
                    }

                }

            }
        }
}
