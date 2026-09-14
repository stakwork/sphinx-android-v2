package chat.sphinx.concept_repository_dashboard_android

/**
 * Clears process-lifetime Graph Chat transcripts on account reset.
 *
 * Implemented by the dashboard [chat.sphinx.dashboard.graphchat.GraphChatHistory]
 * singleton and invoked from [chat.sphinx.feature_repository_android.SphinxRepositoryAndroid]
 * so logout does not leave Jamie history until process death.
 */
fun interface GraphChatHistoryClearer {
    fun clearAll()
}
