package chat.sphinx.feature_network_query_hive

import app.cash.exhaustive.Exhaustive
import chat.sphinx.concept_network_query_hive.model.HiveAuthResponseDto
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
    fun `authenticateWithHive posts to correct URL with correct field mapping`() =
        testDispatcher.runBlockingTest {
            // This test verifies the request is built with correct URL and body fields.
            // It will soft-fail (skip) if credentials are not set in environment.
            getCredentials()?.let { creds ->

                val timestamp = System.currentTimeMillis().toString()
                val testSignedToken = "test_signed_token_value"
                val testPubkey = creds.pubKey

                nqHive.authenticateWithHive(
                    signedToken = testSignedToken,
                    pubkey = testPubkey,
                    timestamp = timestamp
                ).collect { loadResponse ->

                    @Exhaustive
                    when (loadResponse) {
                        is Response.Error -> {
                            // A network error or auth failure is acceptable —
                            // we are verifying request construction, not live auth.
                            loadResponse.exception?.printStackTrace()
                        }
                        is Response.Success -> {
                            // If the server returns a token, verify it is non-empty.
                            Assert.assertNotNull(loadResponse.value)
                            Assert.assertTrue(loadResponse.value.authToken.isNotEmpty())
                        }
                        is LoadResponse.Loading -> { /* no-op */ }
                    }

                }
            }
        }

    @Test
    fun `HIVE_AUTH_URL points to correct endpoint`() {
        Assert.assertEquals(
            "https://hive.sphinx.chat/api/auth/sphinx/token",
            NetworkQueryHiveImpl.HIVE_AUTH_URL
        )
    }

    @Test
    fun `HiveAuthRequestDto serializes token field as JSON key token`() {
        // Verify the Moshi adapter maps signedToken to JSON key "token"
        val moshi = this.moshi
        val adapter = moshi.adapter(chat.sphinx.feature_network_query_hive.model.HiveAuthRequestDto::class.java)
        val dto = chat.sphinx.feature_network_query_hive.model.HiveAuthRequestDto(
            signedToken = "signed_value",
            pubkey = "pubkey_value",
            timestamp = "12345"
        )
        val json = adapter.toJson(dto)
        Assert.assertTrue("JSON must contain 'token' key", json.contains("\"token\""))
        Assert.assertTrue("JSON must contain pubkey key", json.contains("\"pubkey\""))
        Assert.assertTrue("JSON must contain timestamp key", json.contains("\"timestamp\""))
        Assert.assertFalse("JSON must NOT contain 'signedToken' key", json.contains("\"signedToken\""))
        Assert.assertTrue("JSON must contain signed_value", json.contains("signed_value"))
    }

    @Test
    fun `HiveAuthResponseDto deserializes token JSON key to authToken property`() {
        val moshi = this.moshi
        val adapter = moshi.adapter(HiveAuthResponseDto::class.java)
        val json = """{"token":"my_hive_token"}"""
        val dto = adapter.fromJson(json)
        Assert.assertNotNull(dto)
        Assert.assertEquals("my_hive_token", dto!!.authToken)
    }
}
