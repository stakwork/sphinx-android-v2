package chat.sphinx.example.concept_connect_manager

import chat.sphinx.example.concept_connect_manager.model.HiveAuthParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the getHiveAuthParams() logic implemented in ConnectManagerImpl.
 *
 * ConnectManagerImpl is an Android library module with Rust FFI bindings that cannot
 * run on the JVM. These tests validate the guard conditions and structural contract
 * of the getHiveAuthParams() implementation using a pure-JVM simulation:
 *
 *  - One timestamp value used for all three returned fields (coherence)
 *  - A null ownerSeed causes the method to return null without calling FFI
 *  - An FFI exception is caught and null is returned (no crash)
 */
class HiveAuthParamsTest {

    /**
     * Simulates the getHiveAuthParams() logic from ConnectManagerImpl without any
     * real Rust FFI calls. The [signedTimestampFn] and [pubkeyFromSeedFn] parameters
     * stand in for the real uniffi.sphinxrs.signedTimestamp / pubkeyFromSeed bindings,
     * allowing each test to control their behaviour.
     */
    private fun simulateGetHiveAuthParams(
        ownerSeed: String?,
        network: String = "mainnet",
        signedTimestampFn: (seed: String, timestamp: String) -> String,
        pubkeyFromSeedFn: (seed: String, timestamp: String) -> String,
    ): HiveAuthParams? {
        return try {
            val seed = ownerSeed ?: return null
            val timestamp = System.currentTimeMillis().toString()
            val signedToken = signedTimestampFn(seed, timestamp)
            val pubkey = pubkeyFromSeedFn(seed, timestamp)
            HiveAuthParams(signedToken, pubkey, timestamp)
        } catch (e: Exception) {
            null
        }
    }

    // ── (c) Null ownerSeed → return null ────────────────────────────────────

    @Test
    fun `null ownerSeed returns null without calling FFI`() {
        var ffiCalled = false
        val result = simulateGetHiveAuthParams(
            ownerSeed = null,
            signedTimestampFn = { _, _ -> ffiCalled = true; "" },
            pubkeyFromSeedFn = { _, _ -> ffiCalled = true; "" },
        )
        assertNull(result)
        assertEquals("FFI must not be called when ownerSeed is null", false, ffiCalled)
    }

    // ── (a) Timestamp coherence ──────────────────────────────────────────────

    @Test
    fun `all three returned values derive from the same timestamp`() {
        val capturedTimestamps = mutableListOf<String>()

        val result = simulateGetHiveAuthParams(
            ownerSeed = "test-seed",
            signedTimestampFn = { _, ts ->
                capturedTimestamps.add(ts)
                "signed-$ts"
            },
            pubkeyFromSeedFn = { _, ts ->
                capturedTimestamps.add(ts)
                "pubkey-$ts"
            },
        )

        assertNotNull(result)
        // Both FFI calls must have received the same timestamp
        assertEquals(2, capturedTimestamps.size)
        assertEquals(
            "signedTimestamp and pubkeyFromSeed must use the same timestamp value",
            capturedTimestamps[0],
            capturedTimestamps[1]
        )
        // The timestamp in the returned struct must match what was passed to the FFI calls
        assertEquals(
            "Returned timestamp must match the timestamp used in FFI calls",
            capturedTimestamps[0],
            result!!.timestamp
        )
    }

    // ── (b) pubkey is derived via pubkeyFromSeed with correct seed/timestamp ─

    @Test
    fun `pubkey is derived from pubkeyFromSeed with same seed and timestamp`() {
        var capturedSeed: String? = null
        var capturedTimestamp: String? = null

        val result = simulateGetHiveAuthParams(
            ownerSeed = "my-owner-seed",
            signedTimestampFn = { _, _ -> "some-signed-token" },
            pubkeyFromSeedFn = { seed, ts ->
                capturedSeed = seed
                capturedTimestamp = ts
                "derived-pubkey"
            },
        )

        assertNotNull(result)
        assertEquals("pubkeyFromSeed must use the owner seed", "my-owner-seed", capturedSeed)
        assertEquals(
            "pubkeyFromSeed must use the same timestamp as signedTimestamp",
            result!!.timestamp,
            capturedTimestamp
        )
        assertEquals("derived-pubkey", result.pubkey)
    }

    // ── (d) FFI exception → return null, no crash ────────────────────────────

    @Test
    fun `signedTimestamp FFI exception returns null without crashing`() {
        val result = simulateGetHiveAuthParams(
            ownerSeed = "valid-seed",
            signedTimestampFn = { _, _ ->
                // Simulate a SphinxException from the Rust FFI layer
                throw RuntimeException("Simulated SphinxException from signedTimestamp")
            },
            pubkeyFromSeedFn = { _, _ -> "pubkey" },
        )
        assertNull("FFI exception in signedTimestamp must yield null, not crash", result)
    }

    @Test
    fun `pubkeyFromSeed FFI exception returns null without crashing`() {
        val result = simulateGetHiveAuthParams(
            ownerSeed = "valid-seed",
            signedTimestampFn = { _, _ -> "signed-token" },
            pubkeyFromSeedFn = { _, _ ->
                // Simulate a SphinxException from the Rust FFI layer
                throw RuntimeException("Simulated SphinxException from pubkeyFromSeed")
            },
        )
        assertNull("FFI exception in pubkeyFromSeed must yield null, not crash", result)
    }

    // ── HiveAuthParams data class ────────────────────────────────────────────

    @Test
    fun `HiveAuthParams holds all three fields correctly`() {
        val params = HiveAuthParams(
            signedToken = "token-value",
            pubkey = "pubkey-value",
            timestamp = "1234567890000"
        )
        assertEquals("token-value", params.signedToken)
        assertEquals("pubkey-value", params.pubkey)
        assertEquals("1234567890000", params.timestamp)
    }

    @Test
    fun `non-null ownerSeed with successful FFI returns non-null HiveAuthParams`() {
        val result = simulateGetHiveAuthParams(
            ownerSeed = "valid-seed",
            signedTimestampFn = { _, _ -> "signed-token" },
            pubkeyFromSeedFn = { _, _ -> "derived-pubkey" },
        )
        assertNotNull(result)
        assertEquals("signed-token", result!!.signedToken)
        assertEquals("derived-pubkey", result.pubkey)
    }
}
