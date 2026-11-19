package com.ainscel.ainsmail.model

/** 本自然周里，列表和正文上的日期怎么写。本周以外仍是月日。 */
enum class WeekDateStyle {
    /** 今天显示时分，其余显示月日。 */
    Date,

    /** 今天、昨天；同一自然周里更早的显示周几。 */
    Relative,
}
