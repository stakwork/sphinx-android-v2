package chat.sphinx.feature_repository.util

import chat.sphinx.concept_network_query_hive.NetworkQueryHive
import chat.sphinx.kotlin_response.Response
import chat.sphinx.logger.SphinxLogger
import chat.sphinx.logger.d
import io.matthewnelson.concept_authentication.data.AuthenticationStorage
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Encapsulates the Hive authentication flow so it can be unit-tested without
 * constructing the full SphinxRepository.
 *
 * All four dependencies are injected so fakes can be substituted in tests.
 */
class HiveAuthenticator(
    private val networkQueryHive: NetworkQueryHive,
    private val authenticationStorage: AuthenticationStorage,
    private val getSignedTimestamp: () -> String?,
    private val getNodePubKey: () -> String?,
    private val log: SphinxLogger,
) {

    companion object {
        const val HIVE_TOKEN_KEY = "HIVE_AUTH_TOKEN"
        const val HIVE_AUTH_MIN_INTERVAL_MS = 60_000L
        private const val TAG = "HiveAuthenticator"
    }

    private val mutex = Mutex()
    @Volatile private var lastAuthMs = 0L

    suspend fun authenticate() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (now - lastAuthMs < HIVE_AUTH_MIN_INTERVAL_MS) return

            val timestampMs = now.toString()

            val signedToken = getSignedTimestamp()
            if (signedToken == null) {
                log.d(TAG, "[Hive] authenticateWithHive: aborted — signed token unavailable")
                return
            }

            val pubkey = getNodePubKey()
            if (pubkey.isNullOrBlank()) {
                log.d(TAG, "[Hive] authenticateWithHive: aborted — pubkey unavailable")
                return
            }

            networkQueryHive.getHiveAuthToken(signedToken, pubkey, timestampMs).collect { response ->
                when (response) {
                    is Response.Success -> {
                        authenticationStorage.putString(HIVE_TOKEN_KEY, response.value.token)
                        lastAuthMs = System.currentTimeMillis()
                        log.d(TAG, "[Hive] authenticateWithHive: success — token obtained")
                    }
                    is Response.Error -> {
                        log.d(TAG, "[Hive] authenticateWithHive: failed")
                    }
                    else -> { /* Loading — no action */ }
                }
            }
        }
    }

    suspend fun storedToken(): String? =
        authenticationStorage.getString(HIVE_TOKEN_KEY, null)
}
