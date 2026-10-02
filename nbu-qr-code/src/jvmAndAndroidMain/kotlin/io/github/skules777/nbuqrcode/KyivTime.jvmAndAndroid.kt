package io.github.skules777.nbuqrcode

import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone

// java.util.TimeZone rather than java.time: it works on every Android API level
// without core library desugaring.
private val kyivTimeZone: TimeZone =
    listOf("Europe/Kyiv", "Europe/Kiev").firstNotNullOfOrNull { id ->
        // getTimeZone returns GMT for an unknown ID instead of failing.
        TimeZone.getTimeZone(id).takeIf { it.id == id }
    } ?: TimeZone.getTimeZone("GMT+02:00")

internal actual fun kyivDateTimeDigits(epochSeconds: Long): String {
    val calendar = GregorianCalendar(kyivTimeZone).apply {
        gregorianChange = Date(Long.MIN_VALUE)
        timeInMillis = epochSeconds * 1000
    }
    return pad2(calendar.get(Calendar.YEAR) % 100) +
        pad2(calendar.get(Calendar.MONTH) + 1) +
        pad2(calendar.get(Calendar.DAY_OF_MONTH)) +
        pad2(calendar.get(Calendar.HOUR_OF_DAY)) +
        pad2(calendar.get(Calendar.MINUTE)) +
        pad2(calendar.get(Calendar.SECOND))
}
