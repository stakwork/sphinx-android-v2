package chat.sphinx.concept_grapheneos_manager

abstract class GrapheneOsManager {
    abstract fun initializeOptimizations()
    abstract fun <T : Any> optimizeViewContainer(container: T)
}