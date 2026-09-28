package edu.csuft.sap.data.schedule

/** 仅用于展示聚合，保留来源课程，不能用聚合结果覆盖缓存或执行编辑。 */
data class CourseArrangement(val teacher: String, val location: String)

data class CourseGroup(val courses: List<DisplayCourse>) {
    val first: DisplayCourse get() = courses.first()
    val arrangements: List<CourseArrangement> get() = courses.map {
        CourseArrangement(it.teacher.trim(), it.location.trim())
    }.distinct()
    val teachers: List<String> get() = arrangements.map { it.teacher }.distinct()
    val locations: List<String> get() = arrangements.map { it.location }.distinct()
    val teacherSummary: String get() = teachers.singleOrNull()?.ifBlank { "待定" } ?: "${teachers.size} 位教师"
    val locationSummary: String get() = locations.singleOrNull()?.ifBlank { "待定" } ?: "${locations.size} 处教室"
}

private data class CourseGroupKey(val name: String, val day: Int, val start: Int, val end: Int,
                                  val thisWeek: Boolean, val unnamedIndex: Int?)

/** 传入当前周/当天的课程。同名且精确同一时段才合并，不吞掉跨时段课或其他课程。 */
fun groupCourseArrangements(courses: List<DisplayCourse>): List<CourseGroup> = courses.withIndex()
    .groupBy { (index, course) ->
        CourseGroupKey(course.name.trim(), course.day, course.startNode, course.endNode,
            course.isThisWeek, index.takeIf { course.name.isBlank() })
    }.values.map { entries -> CourseGroup(entries.map { it.value }) }
