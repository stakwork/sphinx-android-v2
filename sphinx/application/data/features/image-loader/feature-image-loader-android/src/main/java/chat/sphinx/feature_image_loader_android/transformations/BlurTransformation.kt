package chat.sphinx.feature_image_loader_android.transformations

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.max
import kotlin.math.min

class BlurTransformation(
    private val radius: Float = 10f,
    private val sampling: Float = 1f
) : Transformation {

    override val cacheKey: String = "blur_${radius}_$sampling"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val safeRadius = radius.coerceIn(0f, 25f)

        return if (safeRadius == 0f) {
            input
        } else {
            blurBitmap(input, safeRadius, sampling)
        }
    }

    private fun blurBitmap(input: Bitmap, radius: Float, sampling: Float): Bitmap {
        val width = (input.width / sampling).toInt()
        val height = (input.height / sampling).toInt()

        val inputBitmap = if (sampling != 1f) {
            Bitmap.createScaledBitmap(input, width, height, true)
        } else {
            input
        }

        val outputBitmap = Bitmap.createBitmap(
            inputBitmap.width,
            inputBitmap.height,
            Bitmap.Config.ARGB_8888
        )

        fastBlur(inputBitmap, outputBitmap, radius.toInt())

        return if (sampling != 1f) {
            Bitmap.createScaledBitmap(outputBitmap, input.width, input.height, true)
        } else {
            outputBitmap
        }
    }

    private fun fastBlur(input: Bitmap, output: Bitmap, radius: Int) {
        val pixels = IntArray(input.width * input.height)
        input.getPixels(pixels, 0, input.width, 0, 0, input.width, input.height)

        val w = input.width
        val h = input.height
        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)

        val vmin = IntArray(max(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (i in 0 until 256 * divsum) {
            dv[i] = i / divsum
        }

        var yw = 0
        var yi = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        // Horizontal blur
        for (y in 0 until h) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            var rsum = 0
            var gsum = 0
            var bsum = 0

            for (i in -radius..radius) {
                val p = pixels[yi + min(wm, max(i, 0))]
                sir = stack[i + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                val rbs = r1 - kotlin.math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
            }
            stackpointer = radius

            for (x in 0 until w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (y == 0) {
                    vmin[x] = min(x + radius + 1, wm)
                }
                val p = pixels[yw + vmin[x]]

                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi++
            }
            yw += w
        }

        // Vertical blur
        for (x in 0 until w) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            var rsum = 0
            var gsum = 0
            var bsum = 0
            var yp = -radius * w

            for (i in -radius..radius) {
                val currentYi = max(0, yp) + x

                sir = stack[i + radius]

                sir[0] = r[currentYi]
                sir[1] = g[currentYi]
                sir[2] = b[currentYi]

                val rbs = r1 - kotlin.math.abs(i)

                rsum += r[currentYi] * rbs
                gsum += g[currentYi] * rbs
                bsum += b[currentYi] * rbs

                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }

                if (i < hm) {
                    yp += w
                }
            }

            var currentYi = x
            stackpointer = radius

            for (y in 0 until h) {
                pixels[currentYi] = (0xff000000.toInt() or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum])

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (x == 0) {
                    vmin[y] = min(y + r1, hm) * w
                }
                val p = x + vmin[y]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                currentYi += w
            }
        }

        output.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BlurTransformation) return false

        if (radius != other.radius) return false
        if (sampling != other.sampling) return false

        return true
    }

    override fun hashCode(): Int {
        var result = radius.hashCode()
        result = 31 * result + sampling.hashCode()
        return result
    }
}