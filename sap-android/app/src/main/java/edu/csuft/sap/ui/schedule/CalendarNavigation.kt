package edu.csuft.sap.ui.schedule

import java.time.YearMonth

/** 一次横向手势至多切换一个月，小幅拖动不影响日期点击。 */
internal fun monthAfterSwipe(month: YearMonth, distance: Float, threshold: Float): YearMonth = when {
    distance <= -threshold -> month.plusMonths(1)
    distance >= threshold -> month.minusMonths(1)
    else -> month
}
