package com.ainscel.ainsmail.data.mail

/** 首拉窗口与全库上限，与需求 4.2 对齐。 */
const val FIRST_SYNC_RECENT_DAYS = 7
const val FIRST_SYNC_HISTORY_DAYS = 30
const val FIRST_SYNC_TOTAL_CAP = 50

/**
 * 决定历史补齐还要不要继续。
 * [recentCount]：最近 7 天已入库（可跨账号合计）的封数。
 * [totalCount]：库内当前总封数。
 */
fun shouldFillHistory(
    recentCount: Int,
    totalCount: Int,
    totalCap: Int = FIRST_SYNC_TOTAL_CAP,
): Boolean = recentCount < totalCap && totalCount < totalCap

/** 本轮历史补齐最多再取多少封。 */
fun historyFillBudget(
    totalCount: Int,
    totalCap: Int = FIRST_SYNC_TOTAL_CAP,
): Int = (totalCap - totalCount).coerceAtLeast(0)
