package com.ainscel.ainsmail.ui.mailbox

import com.ainscel.ainsmail.model.Mail
import com.ainscel.ainsmail.model.MailType
import com.ainscel.ainsmail.model.RemoteFolder
import com.ainscel.ainsmail.model.TagCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TimeBucketTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun thisWeekIsMondayThroughSundayAndMayCrossMonth() {
        // 2026-12-02 是周三，这一周是 11-30（周一）到 12-06（周日）。
        val today = at(2026, 12, 2, 23, 0)
        assertEquals(TimeBucket.ThisWeek, timeBucket(at(2026, 12, 2, 0, 1), today, zone))
        assertEquals(TimeBucket.ThisWeek, timeBucket(at(2026, 11, 30, 0, 30), today, zone))
        assertEquals(TimeBucket.Month(11), timeBucket(at(2026, 11, 29, 23, 0), today, zone))
        assertEquals("本周", TimeBucket.ThisWeek.label())
    }

    @Test
    fun thisWeekMayCrossYear() {
        // 2027-01-01 是周五，这一周从 2026-12-28（周一）开始。
        val today = at(2027, 1, 1, 8, 0)
        assertEquals(TimeBucket.ThisWeek, timeBucket(at(2026, 12, 28, 9, 0), today, zone))
        assertEquals(TimeBucket.Year(2026), timeBucket(at(2026, 12, 27, 9, 0), today, zone))
    }

    @Test
    fun laterThisWeekStaysInThisWeekAndNextWeekDoesNot() {
        val today = at(2026, 11, 18, 8, 0)
        assertEquals(TimeBucket.ThisWeek, timeBucket(at(2026, 11, 22, 9, 0), today, zone))
        assertEquals(TimeBucket.EarlierThisMonth, timeBucket(at(2026, 11, 23, 9, 0), today, zone))
    }

    @Test
    fun earlierThisMonthOutsideThisWeekIsEarlierThisMonth() {
        // 2026-11-18 是周三，本周从 11-16 开始。11-15 仍是本月，但已是上一周。
        val today = at(2026, 11, 18, 23, 0)
        assertEquals(TimeBucket.ThisWeek, timeBucket(at(2026, 11, 16, 0, 30), today, zone))
        assertEquals(TimeBucket.EarlierThisMonth, timeBucket(at(2026, 11, 15, 23, 0), today, zone))
        assertEquals("一周前", TimeBucket.EarlierThisMonth.label())
    }

    @Test
    fun olderMonthsAndYearsUseTheirOwnNames() {
        val today = at(2026, 11, 18, 12, 0)
        assertEquals(TimeBucket.Month(10), timeBucket(at(2026, 10, 31, 12, 0), today, zone))
        assertEquals("十月", TimeBucket.Month(10).label())
        assertEquals(TimeBucket.Month(1), timeBucket(at(2026, 1, 2, 12, 0), today, zone))
        assertEquals("一月", TimeBucket.Month(1).label())
        assertEquals(TimeBucket.Year(2025), timeBucket(at(2025, 12, 31, 12, 0), today, zone))
        assertEquals("2025年", TimeBucket.Year(2025).label())
        assertEquals(TimeBucket.Year(2024), timeBucket(at(2024, 6, 1, 12, 0), today, zone))
        assertEquals("2024年", TimeBucket.Year(2024).label())
    }

    @Test
    fun dayJustOutsideAWeekInThePreviousMonthIsThatMonth() {
        val today = at(2026, 12, 3, 8, 0)
        assertEquals(TimeBucket.Month(11), timeBucket(at(2026, 11, 25, 8, 0), today, zone))
        assertEquals("十一月", TimeBucket.Month(11).label())
    }

    @Test
    fun dividersSitAboveTheFirstMailOfEachBucket() {
        val now = at(2026, 11, 18, 9, 0)
        val session = session(
            mail("year-2024", at(2024, 3, 1, 8, 0)),
            mail("oct-late", at(2026, 10, 20, 8, 0)),
            mail("oct-early", at(2026, 10, 2, 8, 0)),
            mail("week", at(2026, 11, 16, 8, 0)),
            mail("year-2025", at(2025, 5, 1, 8, 0)),
            mail("month-ago", at(2026, 11, 1, 8, 0)),
            mail("january", at(2026, 1, 3, 8, 0)),
        )

        val labels = project(session, now, zone = zone).list.entries.map { entry ->
            when (entry) {
                is MailListEntry.TimeDivider -> entry.bucket.label()
                is MailListEntry.Row -> entry.row.id
            }
        }
        assertEquals(
            listOf(
                "本周", "week",
                "一周前", "month-ago",
                "十月", "oct-late", "oct-early",
                "一月", "january",
                "2025年", "year-2025",
                "2024年", "year-2024",
            ),
            labels,
        )
    }

    @Test
    fun hiddenMailDoesNotLeaveAnEmptyDivider() {
        val now = at(2026, 11, 18, 9, 0)
        val session = session(
            mail("kept", at(2026, 10, 2, 8, 0), type = MailType.Important),
            mail("hidden", at(2026, 11, 1, 8, 0), type = MailType.Notice),
        ).copy(typeFilter = MailType.Important)

        val entries = project(session, now, zone = zone).list.entries
        assertEquals(listOf("十月"), entries.filterIsInstance<MailListEntry.TimeDivider>().map { it.bucket.label() })
        assertEquals(listOf("kept"), entries.filterIsInstance<MailListEntry.Row>().map { it.row.id })
    }

    @Test
    fun emptyListHasNoDivider() {
        val entries = project(session(), at(2026, 11, 18, 9, 0), zone = zone).list.entries
        assertTrue(entries.isEmpty())
    }

    @Test
    fun sameMinuteReusesDividersUntilTheNextWeek() {
        val mail = mail("only", at(2026, 11, 16, 8, 0))
        val session = session(mail)
        val firstNow = at(2026, 11, 22, 10, 0)
        val first = project(session, firstNow, zone = zone)
        assertEquals(listOf("本周"), first.list.entries.filterIsInstance<MailListEntry.TimeDivider>().map { it.bucket.label() })

        val reused = project(
            session,
            firstNow + 30_000L,
            reuse = ListReuse(first.list.entries, first.chrome.sectionMailIds, first.list.tagGroups),
            zone = zone,
        )
        assertSame(first.list.entries, reused.list.entries)

        val nextWeek = project(session, at(2026, 11, 23, 10, 0), zone = zone)
        assertEquals(
            listOf("一周前"),
            nextWeek.list.entries.filterIsInstance<MailListEntry.TimeDivider>().map { it.bucket.label() },
        )
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDate.of(year, month, day).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private fun session(vararg mails: Mail): MailboxSession = MailboxSession(
        accounts = emptyList(),
        mails = mails.toList(),
        catalog = TagCatalog(emptyList()),
    )

    private fun mail(
        id: String,
        timeMillis: Long,
        type: MailType? = MailType.Important,
    ): Mail = Mail(
        id = id,
        accountId = "a1",
        remoteFolder = RemoteFolder.Inbox,
        archived = false,
        fromName = "发件人",
        subject = id,
        preview = "",
        body = "",
        timeMillis = timeMillis,
        unread = true,
        type = type,
        tagNames = emptyList(),
    )
}
