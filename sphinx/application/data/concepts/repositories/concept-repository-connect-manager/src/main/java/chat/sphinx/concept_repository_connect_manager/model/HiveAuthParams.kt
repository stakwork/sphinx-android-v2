package chat.sphinx.concept_repository_connect_manager.model

data class HiveAuthParams(
    val signedToken: String,
    val pubkey: String,
    val timestamp: String,
)
