package edu.csuft.sap.widget

/** 仅用于展示，不修改课程提醒的原始列表。 */
fun groupedWidgetCourses(courses: List<WidgetCourse>): List<WidgetCourse> = courses
    .groupBy { listOf(it.name.trim(), it.day, it.startNode, it.endNode) }.values.map { group ->
        val places = group.map { it.location.trim() }.distinct()
        val teachers = group.map { it.teacher.trim() }.distinct()
        group.first().copy(location = places.singleOrNull() ?: "${places.size} 处教室",
            teacher = teachers.singleOrNull() ?: "${teachers.size} 位教师")
    }

fun widgetPeriodCount(courses: List<WidgetCourse>): Int = courses.flatMap { c ->
    (c.startNode.coerceAtLeast(1)..c.endNode.coerceAtMost(16)).map { c.day to it }
}.toSet().size
