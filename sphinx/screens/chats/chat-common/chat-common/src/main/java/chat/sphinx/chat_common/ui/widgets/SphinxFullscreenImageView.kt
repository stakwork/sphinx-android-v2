package chat.sphinx.chat_common.ui.widgets

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.absoluteValue

fun Float.rounded(): Float {
    return ((this*1000).toInt()/1000.0f)
}

class SphinxFullscreenImageView : AppCompatImageView {
    constructor(context: Context) : super(context) { init() }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { init() }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) { init() }

    var onSingleTapListener: OnSingleTapListener? = null
    var onCloseViewHandler: OnCloseViewHandler? = null

    private var scaleFactor = 1.0f
    private var baseScaleFactor = 1.0f
    private var isInitialized = false

    private fun init() {
        scaleType = ScaleType.FIT_CENTER
    }

    private fun initializeImageScale() {
        if (drawable == null || width == 0 || height == 0 || isInitialized) return

        val drawableWidth = drawable.intrinsicWidth.toFloat()
        val drawableHeight = drawable.intrinsicHeight.toFloat()
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // Calculate scale to fit the entire image within the view
        val scaleX = viewWidth / drawableWidth
        val scaleY = viewHeight / drawableHeight
        baseScaleFactor = minOf(scaleX, scaleY)

        // Apply the base scale to fit the image properly
        scaleFactor = 1.0f
        setScaleX(baseScaleFactor)
        setScaleY(baseScaleFactor)

        // Center the image
        translationX = 0f
        translationY = 0f

        isInitialized = true
    }

    private val scaleGestureDetector = ScaleGestureDetector(context, object: ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newScaleFactor = scaleFactor * detector.scaleFactor
            scaleFactor = 0.5f.coerceAtLeast(newScaleFactor.coerceAtMost(5.0f))

            setScaleX((baseScaleFactor * scaleFactor).rounded())
            setScaleY((baseScaleFactor * scaleFactor).rounded())

            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object: GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (!scaleGestureDetector.isInProgress && drawable != null && isInitialized) {
                if (scaleFactor > 1.0f) {
                    // Calculate bounds for panning when zoomed
                    val drawableWidth = drawable.intrinsicWidth.toFloat()
                    val drawableHeight = drawable.intrinsicHeight.toFloat()

                    val currentScale = baseScaleFactor * scaleFactor
                    val scaledDrawableWidth = drawableWidth * currentScale
                    val scaledDrawableHeight = drawableHeight * currentScale

                    val viewWidth = width.toFloat()
                    val viewHeight = height.toFloat()

                    // Calculate maximum translation bounds
                    val maxTransX = maxOf(0f, (scaledDrawableWidth - viewWidth) / 2)
                    val maxTransY = maxOf(0f, (scaledDrawableHeight - viewHeight) / 2)

                    translationX = (translationX - distanceX).coerceIn(-maxTransX, maxTransX)
                    translationY = (translationY - distanceY).coerceIn(-maxTransY, maxTransY)
                } else {
                    // Allow vertical swipe to dismiss when not zoomed
                    translationY -= distanceY

                    if (translationY.absoluteValue > 300) {
                        onCloseViewHandler?.performClose()
                    }
                }

                return true
            }
            return false
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (!scaleGestureDetector.isInProgress) {
                onSingleTapListener?.onSingleTapConfirmed()
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            scaleFactor = if (scaleFactor == 1.0f) {
                2.0f
            } else {
                // Reset to normal scale and center position
                translationX = 0.0f
                translationY = 0.0f
                1.0f
            }

            setScaleX((baseScaleFactor * scaleFactor).rounded())
            setScaleY((baseScaleFactor * scaleFactor).rounded())
            return true
        }
    })

    interface OnSingleTapListener {
        fun onSingleTapConfirmed()
    }

    abstract class OnCloseViewHandler {
        var isInProgress = false

        fun performClose() {
            isInProgress = true
            onCloseView()
        }

        protected abstract fun onCloseView()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (onCloseViewHandler?.isInProgress == true) {
            return true
        }

        if (event.action == MotionEvent.ACTION_UP && scaleFactor == 1.0f && translationY.absoluteValue > 0) {
            // Animate back to center when not zoomed
            animate()
                .translationY(0f)
                .setDuration(300L)
                .start()
        }

        var result = scaleGestureDetector.onTouchEvent(event)
        result = gestureDetector.onTouchEvent(event) || result

        return super.onTouchEvent(event) || result
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            isInitialized = false
            post { initializeImageScale() }
        }
    }

    override fun setImageDrawable(drawable: Drawable?) {
        isInitialized = false
        super.setImageDrawable(drawable)
        post { initializeImageScale() }
    }

    override fun setImageBitmap(bitmap: android.graphics.Bitmap?) {
        isInitialized = false
        super.setImageBitmap(bitmap)
        post { initializeImageScale() }
    }

    override fun setImageResource(resId: Int) {
        isInitialized = false
        super.setImageResource(resId)
        post { initializeImageScale() }
    }

    fun resetInteractionProperties() {
        scaleFactor = 1.0f
        baseScaleFactor = 1.0f
        translationX = 0f
        translationY = 0f
        isInitialized = false
        onCloseViewHandler?.isInProgress = false

        // Reset to identity scale
        setScaleX(1.0f)
        setScaleY(1.0f)

        clearAnimation()

        post { initializeImageScale() }
    }
}