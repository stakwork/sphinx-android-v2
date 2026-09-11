package chat.sphinx.dashboard.graphchat

/**
 * Narrow Hive JWT source for Graph Chat SSE.
 *
 * Wraps [chat.sphinx.feature_repository.SphinxRepository.retrieveHiveToken] /
 * [chat.sphinx.feature_repository.SphinxRepository.authenticateWithHive] without
 * routing through buffered `withHiveToken` / `collectHiveResponse`.
 */
interface HiveTokenSource {
    suspend fun retrieveHiveToken(): String?
    suspend fun authenticateWithHive(): Boolean

    /**
     * Clear the stored JWT and authenticate once, serialized under the existing
     * Hive auth mutex. Used for a single 401 retry.
     */
    suspend fun reauthenticateWithHive(): Boolean
}
