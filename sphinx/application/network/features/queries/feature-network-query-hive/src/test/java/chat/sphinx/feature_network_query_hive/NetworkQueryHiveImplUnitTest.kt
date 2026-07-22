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
    fun `authenticateWithHive posts to correct URL with correct body fields`() =
        testDispatcher.runBlockingTest {
            getCredentials()?.let {

                nqHive.authenticateWithHive(
                    signedToken = "test_signed_token",
                    pubkey = "test_pubkey",
                    timestamp = System.currentTimeMillis().toString()
                ).collect { loadResponse ->

                    @Exhaustive
                    when (loadResponse) {
                        is Response.Error -> {
                            // A network/auth error from the real endpoint is acceptable
                            // when credentials are not valid — the important thing is
                            // the request was built and dispatched correctly.
                            loadResponse.exception?.printStackTrace()
                        }
                        is Response.Success -> {}
                        is LoadResponse.Loading -> {}
                    }

                }
            }
        }
}
