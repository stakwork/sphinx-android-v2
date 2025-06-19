package chat.sphinx

import android.app.Application
import android.util.Log
import android.widget.ImageView
import chat.sphinx.authentication.SphinxAuthenticationCoreManager
import chat.sphinx.concept_grapheneos_manager.GrapheneOsManager
import chat.sphinx.concept_image_loader.ImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SphinxApp: Application() {

    @Inject
    @Suppress("ProtectedInFinal")
    protected lateinit var sphinxAuthenticationCoreManager: SphinxAuthenticationCoreManager

    @Inject
    lateinit var grapheneOsManager: GrapheneOsManager

    @Inject
    lateinit var imageLoaderInj: ImageLoader<ImageView>

    private val imageLoader: ImageLoader<ImageView>
        get() = imageLoaderInj

    override fun onCreate() {
        super.onCreate()
        sphinxAuthenticationCoreManager

        grapheneOsManager.initializeOptimizations()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        when (level) {
            TRIM_MEMORY_RUNNING_CRITICAL,
            TRIM_MEMORY_COMPLETE -> {
                // Critical memory situation - clear all caches aggressively
                imageLoader.clearMemoryCache()
                clearApplicationCaches()

                // Force garbage collection
                System.gc()

                Log.d("SphinxApp", "Aggressive memory cleanup performed (level: $level)")
            }

            TRIM_MEMORY_RUNNING_LOW,
            TRIM_MEMORY_BACKGROUND -> {
                // Moderate memory pressure - clear image cache only
                imageLoader.clearMemoryCache()

                Log.d("SphinxApp", "Image cache cleared (level: $level)")
            }

            TRIM_MEMORY_RUNNING_MODERATE,
            TRIM_MEMORY_UI_HIDDEN -> {
                // Light memory pressure - trim image cache if available
                imageLoader.trimMemory() // This will call clear() as we discussed

                Log.d("SphinxApp", "Memory trimmed (level: $level)")
            }

            TRIM_MEMORY_MODERATE -> {
                // App is in the middle of the background LRU list
                imageLoader.trimMemory()

                Log.d("SphinxApp", "Background memory trimmed (level: $level)")
            }
        }
    }

    private fun clearApplicationCaches() {
        try {
            // Clear app's cache directory
            cacheDir.deleteRecursively()

            // Clear external cache if available
            externalCacheDir?.deleteRecursively()

            // Clear any other app-specific caches you might have
            // For example, if you have database caches, network caches, etc.

        } catch (e: Exception) {
            Log.e("SphinxApp", "Error clearing application caches", e)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()

        // This is called when the system is running critically low on memory
        // Perform the most aggressive cleanup
        imageLoader.clearMemoryCache()
        clearApplicationCaches()
        System.gc()

        Log.w("SphinxApp", "onLowMemory called - performed aggressive cleanup")
    }
}
