package chat.sphinx.grapheneos_manager.optimizers

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import chat.sphinx.concept_image_loader.ImageLoader

class MemoryOptimizer(
    private val context: Context,
    private val imageLoader: ImageLoader<ImageView>
) {

    fun optimizeMemoryUsage() {
        // Monitor memory usage
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory

        val memoryUsagePercentage = (usedMemory * 100) / maxMemory

        Log.d("Memory", "Memory usage: $memoryUsagePercentage%")

        when {
            memoryUsagePercentage > 85 -> {
                // Critical memory usage
                performAggressiveCleanup()
            }
            memoryUsagePercentage > 70 -> {
                // High memory usage
                performModerateCleanup()
            }
        }
    }

    private fun performAggressiveCleanup() {
        // Clear image memory cache
        imageLoader.clearMemoryCache()

        // Suggest garbage collection
        System.gc()

        // Clear other application caches
        clearApplicationCaches()

        Log.d("Memory", "Aggressive memory cleanup performed")
    }

    private fun performModerateCleanup() {
        // Clear only image memory cache
        imageLoader.clearMemoryCache()

        Log.d("Memory", "Moderate memory cleanup performed")
    }

    private fun clearApplicationCaches() {
        try {
            // Clear app's cache directory
            context.cacheDir.deleteRecursively()

            // Clear external cache if available
            context.externalCacheDir?.deleteRecursively()

            Log.d("Memory", "Application caches cleared")
        } catch (e: Exception) {
            Log.e("Memory", "Error clearing application caches", e)
        }
    }

    fun setupMemoryMonitoring() {
        val handler = Handler(Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                optimizeMemoryUsage()
                handler.postDelayed(this, 30000) // Check every 30 seconds
            }
        })
    }

    fun trimMemory() {
        // Trim image cache
        imageLoader.clearMemoryCache() // Since trimToSize is not available

        Log.d("Memory", "Memory trimmed")
    }

    fun getMemoryStats(): String {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val memoryUsagePercentage = (usedMemory * 100) / maxMemory

        return buildString {
            appendLine("=== Memory Stats ===")
            appendLine("Used: ${usedMemory / 1024 / 1024} MB")
            appendLine("Total: ${totalMemory / 1024 / 1024} MB")
            appendLine("Max: ${maxMemory / 1024 / 1024} MB")
            appendLine("Free: ${freeMemory / 1024 / 1024} MB")
            appendLine("Usage: $memoryUsagePercentage%")
        }
    }
}