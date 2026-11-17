package com.ainscel.ainsmail.data.mail

/** 首拉窗口与全库上限，与需求 4.2 对齐。 */
const val FIRST_SYNC_RECENT_DAYS = 7
const val FIRST_SYNC_HISTORY_DAYS = 30
const val FIRST_SYNC_TOTAL_CAP = 50

/** 全库还低于 50 封时才补历史。7 天的信已经在这张表里，不必再单独计一次。 */
fun shouldFillHistory(
    totalCount: Int,
    totalCap: Int = FIRST_SYNC_TOTAL_CAP,
): Boolean = totalCount < totalCap

/** 本轮历史补齐最多再取多少封。 */
fun historyFillBudget(
    totalCount: Int,
    totalCap: Int = FIRST_SYNC_TOTAL_CAP,
): Int = (totalCap - totalCount).coerceAtLeast(0)
