package chat.sphinx.feature_image_loader_android.transformations

import android.graphics.*
import coil.size.Size
import coil.transform.Transformation

class GrayscaleTransformation : Transformation {

    override val cacheKey: String = "grayscale"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = input.width
        val height = input.height

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val colorMatrix = ColorMatrix().apply {
            setSaturation(0f) // This creates grayscale effect
        }

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }

        canvas.drawBitmap(input, 0f, 0f, paint)

        return output
    }

    override fun equals(other: Any?): Boolean {
        return other is GrayscaleTransformation
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}