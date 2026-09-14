package chat.sphinx.graphchat

import chat.sphinx.dashboard.graphchat.HiveTokenSource
import chat.sphinx.feature_repository.SphinxRepository

class HiveTokenSourceImpl(
    private val repository: SphinxRepository,
) : HiveTokenSource {
    override suspend fun retrieveHiveToken(): String? = repository.retrieveHiveToken()

    override suspend fun authenticateWithHive(): Boolean = repository.authenticateWithHive()

    override suspend fun reauthenticateWithHive(): Boolean = repository.reauthenticateWithHive()
}
