package chat.sphinx.dashboard.graphchat

import chat.sphinx.concept_repository_dashboard_android.GraphChatHistoryClearer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GraphChatModule {

    @Binds
    @Singleton
    abstract fun bindGraphChatStreamSource(
        impl: GraphChatSseClient,
    ): GraphChatStreamSource

    @Binds
    @Singleton
    abstract fun bindGraphChatHistoryClearer(
        impl: GraphChatHistory,
    ): GraphChatHistoryClearer
}
