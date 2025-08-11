package chat.sphinx.feature_image_loader_android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Animatable
import android.os.Build
import android.util.DisplayMetrics
import android.widget.ImageView
import androidx.annotation.ContentView
import androidx.annotation.DrawableRes
import app.cash.exhaustive.Exhaustive
import chat.sphinx.concept_image_loader.*
import chat.sphinx.concept_network_client.NetworkClientClearedListener
import chat.sphinx.concept_network_client_cache.NetworkClientCache
import chat.sphinx.feature_image_loader_android.transformations.BlurTransformation
import chat.sphinx.feature_image_loader_android.transformations.GrayscaleTransformation
import chat.sphinx.logger.SphinxLogger
import chat.sphinx.logger.d
import chat.sphinx.logger.e
import coil.annotation.ExperimentalCoilApi
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.SuccessResult
import coil.size.Scale
import coil.size.Size
import coil.transform.CircleCropTransformation
import coil.transform.RoundedCornersTransformation
import coil.transition.CrossfadeTransition
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.CacheControl
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class ImageLoaderAndroid(
    context: Context,
    private val dispatchers: CoroutineDispatchers,
    private val networkClientCache: NetworkClientCache,
    private val LOG: SphinxLogger,
) : ImageLoader<ImageView>(),
    NetworkClientClearedListener,
    CoroutineDispatchers by dispatchers
{

    companion object {
        const val TAG = "ImageLoaderAndroid"
    }

    private val appContext: Context = context.applicationContext

    private var isHighQualityMode = true
    private var isPaused = false

    @Volatile
    private var loader: coil.ImageLoader? = null
    private val loaderLock = Mutex()

    override fun networkClientCleared() {
        loader = null
    }

    init {
        networkClientCache.addListener(this)
    }

    override suspend fun load(
        imageView: ImageView,
        url: String,
        options: ImageLoaderOptions?,
        listener: OnImageLoadListener?,
        isGif: Boolean
    ): Disposable {
        return loadImpl(imageView, url, options, listener, isGif)
    }

    override suspend fun load(
        imageView: ImageView,
        @DrawableRes drawableResId: Int,
        options: ImageLoaderOptions?,
        listener: OnImageLoadListener?,
        isGif: Boolean,
    ): Disposable {
        return loadImpl(imageView, drawableResId, options, listener, isGif)
    }

    override suspend fun load(
        imageView: ImageView,
        file: File,
        options: ImageLoaderOptions?,
        listener: OnImageLoadListener?,
        isGif: Boolean,
    ): Disposable {
        return loadImpl(imageView, file, options, listener, isGif)
    }

    private suspend fun loadImpl(
        imageView: ImageView,
        any: Any,
        options: ImageLoaderOptions?,
        listener: OnImageLoadListener? = null,
        isGif: Boolean = false
    ): Disposable {
        loaderLock.withLock {
            val request = buildRequest(imageView, any, options, listener, isGif)

            val loader: coil.ImageLoader = retrieveLoader()

            return DisposableAndroid(loader.enqueue(request.build()))
        }
    }

    private suspend fun loadImmediateImpl(
        imageView: ImageView,
        any: Any,
        options: ImageLoaderOptions?,
        listener: OnImageLoadListener? = null,
    ) {
        loaderLock.withLock {
            retrieveLoader()
        }.let { loader ->
            val builder = buildRequest(imageView, any, options, listener)
            loader.execute(builder.build())
        }
    }

    private fun calculateOptimalSize(imageView: ImageView): Size {
        val context = imageView.context
        val displayMetrics = context.resources.displayMetrics

        val viewWidth = when {
            imageView.measuredWidth > 0 -> imageView.measuredWidth
            imageView.width > 0 -> imageView.width
            else -> 0
        }

        val viewHeight = when {
            imageView.measuredHeight > 0 -> imageView.measuredHeight
            imageView.height > 0 -> imageView.height
            else -> 0
        }

        return when {
            viewWidth > 0 && viewHeight > 0 -> {
                Size(
                    width = minOf(
                        viewWidth,
                        displayMetrics.widthPixels * 3 / 4
                    ),
                    height = minOf(
                        viewHeight,
                        displayMetrics.heightPixels / 2
                    )
                )
            }

            else -> {
                val baseSize = when {
                    displayMetrics.densityDpi >= DisplayMetrics.DENSITY_XXXHIGH -> 768  // ~480dpi
                    displayMetrics.densityDpi >= DisplayMetrics.DENSITY_XXHIGH -> 640   // ~320dpi
                    displayMetrics.densityDpi >= DisplayMetrics.DENSITY_XHIGH -> 512    // ~240dpi
                    displayMetrics.densityDpi >= DisplayMetrics.DENSITY_HIGH -> 384     // ~160dpi
                    else -> 256
                }

                Size(
                    width = baseSize,
                    height = (baseSize * 0.75f).toInt()
                )
            }
        }
    }

    private fun calculateGifSize(imageView: ImageView): Size {
        val optimal = calculateOptimalSize(imageView)

        return Size(
            width = minOf(optimal.width, 400),
            height = minOf(optimal.height, 400)
        )
    }

    data class Size(val width: Int, val height: Int)

    @OptIn(ExperimentalCoilApi::class)
    private fun buildRequest(
        imageView: ImageView,
        any: Any,
        options: ImageLoaderOptions?,
        listener: OnImageLoadListener? = null,
        isGif: Boolean = false
    ): ImageRequest.Builder {
        val request = ImageRequest.Builder(appContext)
            .data(any)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(if (isPaused) CachePolicy.DISABLED else CachePolicy.ENABLED)
            .allowHardware(false)
            .apply {
                if (!isGif) {
                    val optimalSize = calculateOptimalSize(imageView)

                    if (!isHighQualityMode) {
                        size(optimalSize.width, optimalSize.height)
                        scale(Scale.FIT)
                        bitmapConfig(Bitmap.Config.RGB_565)
                    } else {
                        size(
                            minOf(optimalSize.width * 2, 1024),
                            minOf(optimalSize.height * 2, 1024)
                        )
                        scale(Scale.FIT)
                    }

                    transformations(RoundedCornersTransformation(8f))
                } else {
                    val gifSize = calculateGifSize(imageView)
                    size(gifSize.width, gifSize.height)
                    scale(Scale.FIT)
                }
            }
            .dispatcher(io)
            .listener(
                object: ImageRequest.Listener {
                    private var errorCount = 0

                    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                        super.onSuccess(request, result)

                        listener?.onSuccess()
                    }
                    override fun onError(request: ImageRequest, result: ErrorResult) {
                        super.onError(request, result)
                        errorCount++

                        LOG.d(TAG, "Image load failed: ${result.throwable.message}")

                        if (errorCount >= 2) {
                            listener?.onError()
                        }
                    }
                }
            )
            .target(imageView)

        options?.let {
            it.errorResId?.let { errorRes ->
                request.error(errorRes)
            }
            it.placeholderResId?.let { placeholderRes ->
                request.placeholder(placeholderRes)
            }
            it.transformation?.let { transform ->
                @Exhaustive
                when (transform) {
                    is Transformation.Blur -> {
                        request.transformations(
                            BlurTransformation(
                                transform.radius,
                                transform.sampling
                            )
                        )
                    }
                    is Transformation.CircleCrop -> {
                        request.transformations(
                            CircleCropTransformation()
                        )
                    }
                    is Transformation.GrayScale -> {
                        request.transformations(
                            GrayscaleTransformation()
                        )
                    }
                    is Transformation.RoundedCorners -> {
                        request.transformations(
                            RoundedCornersTransformation(
                                transform.topLeft.value,
                                transform.topRight.value,
                                transform.bottomLeft.value,
                                transform.bottomRight.value
                            )
                        )
                    }
                }
            }

            it.transition.let { transition ->
                @Exhaustive
                when (transition) {
                    is Transition.CrossFade -> {
                        request.transitionFactory { target, result ->
                            CrossfadeTransition(
                                target,
                                result,
                                transition.durationMillis,
                                transition.preferExactIntrinsicSize
                            )
                        }
                    }
                    is Transition.None -> {}
                }
            }

            for (entry in it.additionalHeaders.entries) {
                try {
                    request.addHeader(entry.key, entry.value)
                } catch (e: Exception) {
                    LOG.e(TAG, "Failed to add header to request", e)
                }
            }
        }

        return request
    }

    private fun retrieveLoader(): coil.ImageLoader =
        loader ?: coil.ImageLoader.Builder(appContext)
            .memoryCache(
                MemoryCache.Builder(appContext)
                    .maxSizePercent(0.25)
                    .strongReferencesEnabled(false)
                    .build()
            )
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100 * 1024 * 1024)
                    .cleanupDispatcher(Dispatchers.IO.limitedParallelism(1))
                    .build()
            }
            .okHttpClient(
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
                    .addInterceptor(createNetworkInterceptor())
                    .build()
            )
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                }
                add(GifDecoder.Factory())
                add(SvgDecoder.Factory())
            }
            .dispatcher(Dispatchers.IO.limitedParallelism(5))
            .respectCacheHeaders(false)
            .allowHardware(false)
            .crossfade(200)
            .build()
            .also { loader = it }


    private fun createNetworkInterceptor() = Interceptor { chain ->
        val request = chain.request()

        if (isPaused) {
            // Return cached response if network is paused
            val cacheOnlyRequest = request.newBuilder()
                .cacheControl(CacheControl.FORCE_CACHE)
                .build()
            chain.proceed(cacheOnlyRequest)
        } else {
            var response = chain.proceed(request)
            var retryCount = 0

            while (!response.isSuccessful && retryCount < 3) {
                response.close()
                retryCount++
                Thread.sleep((500 * Math.pow(2.0, retryCount.toDouble())).toLong())
                response = chain.proceed(request)
            }

            // Add aggressive caching headers for GrapheneOS
            response.newBuilder()
                .header("Cache-Control", "public, max-age=86400") // 24 hours
                .build()
        }
    }

    override fun preloadImages(urls: List<String>) {
        if (isPaused || !isHighQualityMode) return

        urls.forEach { url ->
            val request = ImageRequest.Builder(appContext)
                .data(url)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .size(256, 256) // Smaller preload size
                .build()

            retrieveLoader().enqueue(request)
        }
    }

    override fun setHighQualityMode(enabled: Boolean) {
        isHighQualityMode = enabled
    }

    override fun pauseImageLoading() {
        isPaused = true
    }

    override fun resumeImageLoading() {
        isPaused = false
    }

    override fun clearMemoryCache() {
        retrieveLoader().memoryCache?.clear()
    }

    override fun getCacheStats(): String {
        val memoryCache = loader?.memoryCache
        val diskCache = loader?.diskCache

        return buildString {
            appendLine("=== ImageLoader Cache Stats ===")
            memoryCache?.let {
                appendLine("Memory Cache: ${it.size}/${it.maxSize} bytes")
            }
            diskCache?.let {
                appendLine("Disk Cache: ${it.size}/${it.maxSize} bytes")
            }
        }
    }

    override fun trimMemory() {
        loader?.memoryCache?.clear()
    }
}

