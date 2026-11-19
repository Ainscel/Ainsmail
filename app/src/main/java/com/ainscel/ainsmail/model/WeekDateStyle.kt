package com.ainscel.ainsmail.model

/** 本自然周里，列表和正文上的日期怎么写。今天两种模式都只显示时分。本周以外仍是月日。 */
enum class WeekDateStyle {
    /** 昨天起显示月日。 */
    Date,

    /** 昨天起：昨天，同一自然周里更早的显示周几。 */
    Relative,
}
