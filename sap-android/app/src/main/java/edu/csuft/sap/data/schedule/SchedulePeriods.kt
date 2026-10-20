package edu.csuft.sap.data.schedule

/**
 * 当前整份课表的显示节数：课程超出默认范围时，按完整两节扩展（第 11 节也显示到第 12 节）。
 * 只派生显示范围，不写回设备偏好，切换课表或移除晚课后可恢复原排版。
 */
fun ScheduleSettings.displayPeriodCount(courses: List<DisplayCourse>): Int {
    val lastNode = courses.asSequence()
        .filter { it.day in 1..7 && it.startNode in 1..Periods.MAX_NODES &&
            it.endNode in it.startNode..Periods.MAX_NODES }
        .maxOfOrNull { it.endNode } ?: 0
    val required = ((lastNode + 1) / 2) * 2
    return maxOf(dailyPeriods, required)
}
