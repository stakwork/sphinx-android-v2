package chat.sphinx.wrapper_common.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

@Suppress("NOTHING_TO_INLINE")
inline fun String.getInitials(charLimit: Int = 2): String {
    val sb = StringBuilder()
    this.split(' ').let { splits ->
        for ((index, split) in splits.withIndex()) {
            if (index < charLimit) {
                sb.append(split.firstOrNull() ?: "")
            } else {
                break
            }
        }
    }
    return sb.toString()
}

//DateTime
@Suppress("NOTHING_TO_INLINE")
inline fun Long.getHHMMSSString(): String {
    val hours: Int
    val minutes: Int
    var seconds: Int = this.toInt() / 1000

    hours = seconds / 3600
    minutes = seconds / 60 % 60
    seconds %= 60

    val hoursString = if (hours < 10) "0${hours}" else "$hours"
    val minutesString = if (minutes < 10) "0${minutes}" else "$minutes"
    val secondsString = if (seconds < 10) "0${seconds}" else "$seconds"

    return "$hoursString:$minutesString:$secondsString"
}

@Suppress("NOTHING_TO_INLINE")
inline fun Long.getHHMMString(): String {
    val minutes = this / 1000 / 60
    val seconds = this / 1000 % 60

    return "${"%02d".format(minutes)}:${"%02d".format(seconds)}"
}
@Suppress("NOTHING_TO_INLINE")
inline fun Long.toFormattedDate(): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = this

    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val month = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.ENGLISH)
    val year = calendar.get(Calendar.YEAR)

    val daySuffix = when {
        day in 11..13 -> "th" // handle 11th-13th
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }

    return "$month $day$daySuffix $year"
}