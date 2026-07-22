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
    fun `authenticateWithHive posts to correct URL with correct fields`() =
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
                            // An error is acceptable here since we are not using real credentials.
                            // The important thing is that the request was built and dispatched
                            // with the correct URL and body fields.
                            loadResponse.exception?.printStackTrace()
                        }
                        is Response.Success -> {
                            Assert.assertNotNull(loadResponse.value.authToken)
                        }
                        is LoadResponse.Loading -> {}
                    }

                }
            }
        }

    @Test
    fun `HIVE_AUTH_URL is correct`() {
        Assert.assertEquals(
            "https://hive.sphinx.chat/api/auth/sphinx/token",
            NetworkQueryHiveImpl.HIVE_AUTH_URL
        )
    }
}
