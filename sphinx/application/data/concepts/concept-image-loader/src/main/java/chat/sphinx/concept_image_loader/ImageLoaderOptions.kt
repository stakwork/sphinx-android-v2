package chat.sphinx.concept_image_loader

import chat.sphinx.wrapper_view.Px

@Suppress("DataClassPrivateConstructor")
data class ImageLoaderOptions private constructor(
    val transformation: Transformation?,
    val transition: Transition,
    val errorResId: Int?,
    val placeholderResId: Int?,
    val additionalHeaders: Map<String, String>,
    val targetSize: Size? = null,
    val sizeHint: ImageSizeHint? = null
) {

    class Builder {
        private var transformation: Transformation? = null
        private var transition: Transition = Transition.None
        private var errorResId: Int? = null
        private var placeholderResId: Int? = null
        private val additionalHeaders: MutableMap<String, String> = LinkedHashMap(0)
        private var targetSize: Size? = null
        private var sizeHint: ImageSizeHint? = null

        fun targetSize(width: Int, height: Int) = apply {
            this.targetSize = Size(width, height)
        }

        fun sizeHint(hint: ImageSizeHint) = apply {
            this.sizeHint = hint
        }

        fun transformation(transformation: Transformation) = apply {
            this.transformation = transformation
        }

        fun transition(transition: Transition) = apply {
            this.transition = transition
        }

        fun errorResId(resourceId: Int) = apply {
            this.errorResId = resourceId
        }

        fun placeholderResId(resourceId: Int) = apply {
            this.placeholderResId = resourceId

            if (this.errorResId == null) {
                this.errorResId = resourceId
            }
        }

        fun addHeader(key: String, value: String) = apply {
            if (key.isNotEmpty() && value.isNotEmpty()) {
                additionalHeaders[key] = value
            }
        }

        fun build() = ImageLoaderOptions(
            transformation,
            transition,
            errorResId,
            placeholderResId,
            additionalHeaders,
            targetSize,
            sizeHint
        )
    }

}

sealed class Transformation {

    data class Blur(
        val radius: Float = DEFAULT_RADIUS,
        val sampling: Float = DEFAULT_SAMPLING,
    ): Transformation() {
        companion object {
            const val DEFAULT_RADIUS = 10f
            const val DEFAULT_SAMPLING = 1f
        }
    }

    object CircleCrop: Transformation()
    object GrayScale: Transformation()

    data class RoundedCorners(
        val topLeft: Px = Px(0f),
        val topRight: Px = Px(0f),
        val bottomLeft: Px = Px(0f),
        val bottomRight: Px = Px(0f),
    ): Transformation()
}

sealed class Transition {

    data class CrossFade(
        val durationMillis: Int = DEFAULT_DURATION,
        val preferExactIntrinsicSize: Boolean = false
    ): Transition() {
        companion object {
            const val DEFAULT_DURATION = 100
        }
    }

    object None: Transition()
}

enum class ImageSizeHint {
    AUTO,
    THUMBNAIL,
    PROFILE_PICTURE,
    CHAT_IMAGE
}

data class Size(val width: Int, val height: Int)