package com.ainscel.ainsmail.model

/** 全部邮箱里，寄件人圆右下角那个收件账户小圆怎么重复。 */
enum class AccountBadgeStyle {
    /** 每一行都画。 */
    EveryRow,

    /** 连续同一收件账户只在第一行画。中间换成别的账户后，再画一次。 */
    FirstOfRun,

    /** 同一收件账户里，连续相同寄件人只在第一行画。寄件人或账户变了都再画。 */
    SameSender,
}
