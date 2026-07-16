package chat.sphinx.concept_hive_token

/**
 * Persists and retrieves Hive auth token to device storage. Implementation
 * requires User to be logged in to work, otherwise `null` and `false` are always
 * returned.
 */
abstract class HiveTokenHandler {
    abstract suspend fun persistHiveToken(token: String): Boolean
    abstract suspend fun retrieveHiveToken(): String?
    abstract suspend fun clearHiveToken(): Boolean
}
