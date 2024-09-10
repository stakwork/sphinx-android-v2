package chat.sphinx.highlighting_tool

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import java.util.*


/**
 * LinkifyCompat brings in `Linkify` improvements for URLs and email addresses to older API
 * levels.
 */
@SuppressLint("RestrictedApi")
object SphinxHighlightingTool {
    /**
     * Scans the text of the provided TextView and turns all occurrences of
     * the link types indicated in the mask into clickable links.  If matches
     * are found the movement method for the TextView is set to
     * LinkMovementMethod.
     *
     * @param text TextView whose text is to be marked-up with links
     * @param mask Mask to define which kinds of links will be searched.
     *
     * @return True if at least one link is found and applied.
     */
    fun addMarkdowns(
        text: TextView,
        highlightedTexts: List<Pair<String, IntRange>>,
        boldTexts: List<Pair<String, IntRange>>,
        linkTexts: List<Pair<String, IntRange>>,
        onSphinxInteractionListener: SphinxUrlSpan.OnInteractionListener?,
        resources: Resources,
        context: Context
    ) {
        if (highlightedTexts.isNotEmpty()) {
            val t = text.text

            if (t is Spannable) {
                for (highlightedText in highlightedTexts) {
                    ResourcesCompat.getFont(context, R.font.roboto_light)?.let { typeface ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            t.setSpan(
                                TypefaceSpan(typeface),
                                highlightedText.second.first,
                                highlightedText.second.last,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                    }

                    t.setSpan(
                        BackgroundColorSpan(resources.getColor(R.color.highlightedTextBackground)),
                        highlightedText.second.first,
                        highlightedText.second.last,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )

                    t.setSpan(object : MetricAffectingSpan() {
                        override fun updateMeasureState(paint: TextPaint) {
                            paint.baselineShift -= 8  // Adjust baseline to reduce padding
                        }

                        override fun updateDrawState(tp: TextPaint) {
                            tp.baselineShift -= 8  // Same adjustment for drawing
                        }
                    }, 0, t.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                    text.setText(t, TextView.BufferType.SPANNABLE)
                }
            } else {
                val spannable: Spannable = SpannableString(text.text)

                for (highlightedText in highlightedTexts) {
                    ResourcesCompat.getFont(context, R.font.roboto_light)?.let { typeface ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            spannable.setSpan(
                                TypefaceSpan(typeface),
                                highlightedText.second.first,
                                highlightedText.second.last,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                    }

                    spannable.setSpan(
                        BackgroundColorSpan(resources.getColor(R.color.highlightedTextBackground)),
                        highlightedText.second.first,
                        highlightedText.second.last,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )

                    spannable.setSpan(object : MetricAffectingSpan() {
                        override fun updateMeasureState(paint: TextPaint) {
                            paint.baselineShift -= 8  // Adjust baseline to reduce padding
                        }

                        override fun updateDrawState(tp: TextPaint) {
                            tp.baselineShift -= 8  // Same adjustment for drawing
                        }
                    }, 0, t.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                    text.setText(spannable, TextView.BufferType.SPANNABLE)
                }
            }
        }

        if (boldTexts.isNotEmpty()) {
            val t = text.text

            if (t is Spannable) {
                for (boldText in boldTexts) {
                    ResourcesCompat.getFont(context, R.font.roboto_black)?.let { typeface ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            t.setSpan(
                                TypefaceSpan(typeface),
                                boldText.second.first,
                                boldText.second.last,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                    }

                    text.setText(t, TextView.BufferType.SPANNABLE)
                }
            } else {
                val spannable: Spannable = SpannableString(text.text)

                for (boldText in boldTexts) {
                    ResourcesCompat.getFont(context, R.font.roboto_black)?.let { typeface ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            spannable.setSpan(
                                TypefaceSpan(typeface),
                                boldText.second.first,
                                boldText.second.last,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                    }

                    text.setText(spannable, TextView.BufferType.SPANNABLE)
                }
            }
        }

        if (linkTexts.isNotEmpty()) {
            val t = text.text

            if (t is Spannable) {
                for (linkText in linkTexts) {
                    val span = SphinxUrlSpan(
                        linkText.first,
                        true,
                        context.getColor(
                            R.color.primaryBlue
                        ),
                        onSphinxInteractionListener
                    )

                    t.setSpan(
                        span,
                        linkText.second.first,
                        linkText.second.last,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )

                    text.setText(t, TextView.BufferType.SPANNABLE)
                }
            } else {
                val spannable: Spannable = SpannableString(text.text)

                for (linkText in linkTexts) {
                    val span = SphinxUrlSpan(
                        linkText.first,
                        true,
                        context.getColor(
                            R.color.primaryBlue
                        ),
                        onSphinxInteractionListener
                    )

                    spannable.setSpan(
                        span,
                        linkText.second.first,
                        linkText.second.last + 1,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )

                    text.movementMethod = LinkMovementMethod.getInstance()
                    text.setText(spannable, TextView.BufferType.SPANNABLE)
                    text.invalidate()
                }
            }
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun String.highlightedTexts(): List<Pair<String, IntRange>> {
    val matcher = "`([^`]*)`".toRegex()
    val ranges = matcher.findAll(this).map{ it.range }.toList()

    if (ranges.isEmpty()) {
        return emptyList()
    }

    var adaptedText = this
    var matches: MutableList<Pair<String, IntRange>> = mutableListOf()

    ranges.forEach { range ->
        val rangeString = adaptedText.substring(range)

        matches.add(
            Pair(rangeString, range)
        )
    }

    return matches
}

@Suppress("NOTHING_TO_INLINE")
inline fun String.boldTexts(): List<Pair<String, IntRange>> {
    val matcher = "\\*\\*(.*?)\\*\\*".toRegex()
    val ranges = matcher.findAll(this).map{ it.range }.toList()

    if (ranges.isEmpty()) {
        return emptyList()
    }

    var adaptedText = this
    var matches: MutableList<Pair<String, IntRange>> = mutableListOf()

    ranges.forEach { range ->
        val rangeString = adaptedText.substring(range)

        matches.add(
            Pair(rangeString, range)
        )
    }

    return matches
}

@Suppress("NOTHING_TO_INLINE")
inline fun String.markDownLinkTexts(): List<Pair<String, IntRange>> {
    val matcher = "!?\\[(.*?)\\]\\((https?:\\/\\/[^\\s)]+)\\)".toRegex()
    val ranges = matcher.findAll(this).map{ it.range }.toList()

    if (ranges.isEmpty()) {
        return emptyList()
    }

    var adaptedText = this
    var matches: MutableList<Pair<String, IntRange>> = mutableListOf()

    ranges.forEach { range ->
        val rangeString = adaptedText.substring(range)
        val match = matcher.find(rangeString)

        if (match != null) {
            matches.add(
                Pair(match.groups[2]?.value ?: "", range)
            )
        }
    }

    return matches
}

inline fun String.replacingMarkdown(): String {
    return this.trim().replacingHighlightedDelimiters().replacingBoldDelimiters().replacingDashWithBullets().replacingMarkdownLinks().markdownTrim()
}

@Suppress("NOTHING_TO_INLINE")
inline fun String.replacingDashWithBullets(): String {
    val matcher = "(?:\\r?\\n)-".toRegex()
    val ranges = matcher.findAll(this).map{ it.range }.toList()

    if (ranges.isEmpty()) {
        return this
    }

    var adaptedText = this

    ranges.forEach { range ->
        val rangeString = adaptedText.substring(range).replace("-", "•")
        adaptedText = adaptedText.replaceRange(range, rangeString)
    }

    return adaptedText
}

@Suppress("NOTHING_TO_INLINE")
inline fun String.markdownTrim(): String {
    if (this.length < 3) {
        return this
    }

    var trimmedString = this
    val zeroWidthSpace = "\u200B"

    ///Replace new line with empty space if it starts with highlight char and new line
    if (this.startsWith("$zeroWidthSpace\n")) {
        val range: IntRange = 0..1
        val rangeString = trimmedString.substring(range).replace(
            "$zeroWidthSpace\n",
            "$zeroWidthSpace$zeroWidthSpace"
        )
        trimmedString = trimmedString.replaceRange(range, rangeString)
    }

    if (trimmedString.startsWith("$zeroWidthSpace$zeroWidthSpace\n")) {
        val range: IntRange = 0..2
        val rangeString = trimmedString.substring(range).replace(
            "$zeroWidthSpace$zeroWidthSpace\n",
            "$zeroWidthSpace$zeroWidthSpace$zeroWidthSpace"
        )
        trimmedString = trimmedString.replaceRange(range, rangeString)
    }

    if (trimmedString.endsWith("\n$zeroWidthSpace")) {
        val range: IntRange = (trimmedString.length - 2) until trimmedString.length
        val rangeString = trimmedString.substring(range).replace(
            "\n$zeroWidthSpace",
            "$zeroWidthSpace$zeroWidthSpace"
        )
        trimmedString = trimmedString.replaceRange(range, rangeString)
    }

    if (trimmedString.endsWith("\n$zeroWidthSpace$zeroWidthSpace")) {
        val range: IntRange = (trimmedString.length - 3) until trimmedString.length
        val rangeString = trimmedString.substring(range).replace(
            "\n$zeroWidthSpace$zeroWidthSpace",
            "$zeroWidthSpace$zeroWidthSpace$zeroWidthSpace"
        )
        trimmedString = trimmedString.replaceRange(range, rangeString)
    }

    return trimmedString
}

@Suppress("NOTHING_TO_INLINE")
inline fun String.replacingHighlightedDelimiters(): String {
    val matcher = "`([^`]*)`".toRegex()
    val ranges = matcher.findAll(this).map{ it.range }.toList()

    if (ranges.isEmpty()) {
        return this
    }

    var adaptedText = this
    val invisibleChar = "\u200B"

    ranges.forEach { range ->
        val rangeString = adaptedText.substring(range).replace("`", invisibleChar)
        adaptedText = adaptedText.replaceRange(range, rangeString)
    }

    return adaptedText
}

@Suppress("NOTHING_TO_INLINE")
inline fun String.replacingBoldDelimiters(): String {
    val matcher = "\\*\\*([^`]*)\\*\\*".toRegex()
    val ranges = matcher.findAll(this).map{ it.range }.toList()

    if (ranges.isEmpty()) {
        return this
    }

    var adaptedText = this
    val invisibleChars = "\u200B\u200B"

    ranges.forEach { range ->
        val rangeString = adaptedText.substring(range).replace("**", invisibleChars)
        adaptedText = adaptedText.replaceRange(range, rangeString)
    }

    return adaptedText
}

@Suppress("NOTHING_TO_INLINE")
inline fun String.replacingMarkdownLinks(): String {
    val matcher = "!?\\[(.*?)\\]\\((https?:\\/\\/[^\\s)]+)\\)".toRegex()
    val ranges = matcher.findAll(this).map{ it.range }.toList()

    if (ranges.isEmpty()) {
        return this
    }

    var adaptedText = this
    val invisibleChars = "\u200B"

    ranges.forEach { range ->
        var rangeString = adaptedText.substring(range)

        val result = matcher.replace(rangeString) { matchResult ->
            val anyText = matchResult.groups[1]?.value ?: ""

            val prefix = matchResult.value.substringBefore(anyText) // Get everything before AnyText
            val suffix = matchResult.value.substringAfter(anyText)  // Get everything after AnyText

            // Replace each character in prefix and suffix with an invisible character
            val invisiblePrefix = prefix.map { "$invisibleChars" }.joinToString("")
            val invisibleSuffix = suffix.map { "$invisibleChars" }.joinToString("")

            invisiblePrefix + anyText + invisibleSuffix
        }

        adaptedText = adaptedText.replaceRange(range, result)
    }

    return adaptedText
}