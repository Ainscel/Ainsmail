package com.ainscel.ainsmail.ui.mailbox

import com.ainscel.ainsmail.model.WeekDateStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class WeekDateFormatTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun dateStyleKeepsClockTodayAndMonthDayOtherwise() {
        val now = at(2026, 11, 18, 9, 5)
        assertEquals("09:05", format(at(2026, 11, 18, 9, 5), now, WeekDateStyle.Date))
        assertEquals("11月16日", format(at(2026, 11, 16, 8, 0), now, WeekDateStyle.Date))
        assertEquals("10月20日", format(at(2026, 10, 20, 8, 0), now, WeekDateStyle.Date))
    }

    @Test
    fun relativeStyleStartsFromYesterday() {
        // 2026-11-18 是周三，本周从 11-16（周一）到 11-22（周日）。
        val now = at(2026, 11, 18, 9, 5)
        assertEquals("08:00", format(at(2026, 11, 18, 8, 0), now, WeekDateStyle.Relative))
        assertEquals("昨天", format(at(2026, 11, 17, 23, 0), now, WeekDateStyle.Relative))
        assertEquals("周一", format(at(2026, 11, 16, 8, 0), now, WeekDateStyle.Relative))
        assertEquals("周五", format(at(2026, 11, 20, 8, 0), now, WeekDateStyle.Relative))
        assertEquals("周日", format(at(2026, 11, 22, 8, 0), now, WeekDateStyle.Relative))
        assertEquals("11月15日", format(at(2026, 11, 15, 8, 0), now, WeekDateStyle.Relative))
        assertEquals("10月20日", format(at(2026, 10, 20, 8, 0), now, WeekDateStyle.Relative))
    }

    @Test
    fun yesterdayStaysYesterdayWhenItFallsInThePreviousWeek() {
        // 2026-11-16 是周一。昨天是 11-15，已不属于这一周。
        val now = at(2026, 11, 16, 10, 0)
        assertEquals("昨天", format(at(2026, 11, 15, 21, 0), now, WeekDateStyle.Relative))
        assertEquals("11月14日", format(at(2026, 11, 14, 21, 0), now, WeekDateStyle.Relative))
    }

    private fun format(mail: Long, now: Long, style: WeekDateStyle): String =
        formatMailTime(mail, now, zone, style)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDate.of(year, month, day).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
}
