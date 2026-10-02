package io.github.skules777.nbuqrcode

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneForSecondsFromGMT
import platform.Foundation.timeZoneWithName

private val kyivTimeZone: NSTimeZone =
    NSTimeZone.timeZoneWithName("Europe/Kyiv")
        ?: NSTimeZone.timeZoneWithName("Europe/Kiev")
        ?: NSTimeZone.timeZoneForSecondsFromGMT(2L * 3600)

internal actual fun kyivDateTimeDigits(epochSeconds: Long): String {
    val calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)
    calendar.timeZone = kyivTimeZone
    val units = NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
        NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond
    val c = calendar.components(units, fromDate = NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble()))
    return pad2((c.year % 100).toInt()) + pad2(c.month.toInt()) + pad2(c.day.toInt()) +
        pad2(c.hour.toInt()) + pad2(c.minute.toInt()) + pad2(c.second.toInt())
}
