package io.github.flyingpig525.std

import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

fun LocalDateTime.render(): String {
    val now = LocalDateTime.now()
    val locale = Locale.getDefault()
    val dayOfWeek = dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    val month = month.getDisplayName(TextStyle.SHORT, locale)
    val showYear = year != now.year
    val pm = hour > 11
    val hourNum = if (pm) hour - 11 else hour + 1
    val minute = if (minute < 10) "0$minute" else "$minute"
    if (now.toLocalDate() == toLocalDate()) {
        return "Today, $hourNum:$minute ${if (pm) "pm" else "am"}"
    }
    val txt = "$hourNum:$minute ${if (pm) "pm" else "am"}, $dayOfWeek $month $dayOfMonth${if (showYear) ", $year" else ""}"
    return txt
}

fun main() {
    val a = LocalDateTime.now().minusMonths(2)
    val b = LocalDateTime.now().minusMinutes(40)
    println(a.render())
    println(b.render())
}