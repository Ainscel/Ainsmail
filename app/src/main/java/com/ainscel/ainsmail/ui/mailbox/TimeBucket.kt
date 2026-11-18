package com.ainscel.ainsmail.ui.mailbox

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 列表上的时间档。只在投影里计算，不进邮件，也不进归约。
 * 相邻两封相等就不再插入分隔；[listKey] 给列表做稳定 key。
 */
sealed interface TimeBucket {
    data object ThisWeek : TimeBucket
    data object EarlierThisMonth : TimeBucket
    data class Month(val month: Int) : TimeBucket
    data class Year(val year: Int) : TimeBucket
}

private val MonthLabels = arrayOf(
    "一月", "二月", "三月", "四月", "五月", "六月",
    "七月", "八月", "九月", "十月", "十一月", "十二月",
)

/** 分隔线下面靠左的档位名。 */
fun TimeBucket.label(): String = when (this) {
    TimeBucket.ThisWeek -> "本周"
    TimeBucket.EarlierThisMonth -> "一周前"
    is TimeBucket.Month -> MonthLabels[month - 1]
    is TimeBucket.Year -> "${year}年"
}

/** LazyColumn 的 key。带前缀，避免和邮件 id 撞上。 */
fun TimeBucket.listKey(): String = when (this) {
    TimeBucket.ThisWeek -> "time/week"
    TimeBucket.EarlierThisMonth -> "time/earlier-month"
    is TimeBucket.Month -> "time/month/$month"
    is TimeBucket.Year -> "time/year/$year"
}

/**
 * 一封信只进第一档，按本地日期而不是满 24 小时。
 * 距今天不超过 7 天（含第 7 天）归本周，跨月、跨年也一样。
 * 日期晚于今天时天数差为负，同样归本周。
 * 否则还在本月归「一周前」；还在本年按月份；更早按年份。
 */
fun timeBucket(epochMillis: Long, nowMillis: Long, zone: ZoneId): TimeBucket {
    val mailDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    if (ChronoUnit.DAYS.between(mailDate, today) <= 7) return TimeBucket.ThisWeek
    if (mailDate.year == today.year && mailDate.monthValue == today.monthValue) {
        return TimeBucket.EarlierThisMonth
    }
    if (mailDate.year == today.year) return TimeBucket.Month(mailDate.monthValue)
    return TimeBucket.Year(mailDate.year)
}
