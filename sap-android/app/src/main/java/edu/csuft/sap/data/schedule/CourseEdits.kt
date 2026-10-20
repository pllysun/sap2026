package edu.csuft.sap.data.schedule

import java.security.MessageDigest

/** 不依赖返回顺序或数据库行 ID，重新拉取同一底本后仍保留本地编辑。 */
fun CachedCourse.sourceId(): String = sourceIdForWeeks(WeekUtil.parseWeeks(weeksRaw))

private fun CachedCourse.sourceIdForWeeks(weeks: List<Int>, includeNodes: Boolean = true): String {
    val fields = mutableListOf(name, teacher, location, day.toString(), sectionIndex.toString(),
        weeks.sorted().joinToString(","))
    val actual = nodeRanges().flatMap { it.toList() }
    if (includeNodes && actual != Periods.nodesOfSection(sectionIndex).toList())
        fields += actual.joinToString(",")
    val canonical = fields.joinToString("") { "${it.length}:$it" }
    return "source:" + MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

// 仅用于识别旧版保存的隐藏/编辑键，排课判断始终使用修正后的 WeekUtil。
private fun CachedCourse.legacySourceId(): String {
    val raw=weeksRaw.orEmpty()
    val parity=when { raw.contains("单") -> 1; raw.contains("双") -> 0; else -> -1 }
    val weeks=sortedSetOf<Int>()
    for(part in raw.substringBefore('(').replace("周", "").trim().split(',', '，')) {
        val bounds=part.trim().split('-', '~').map { it.trim().toIntOrNull() }
        if(bounds.size==2) {
            val a=bounds[0] ?: continue;val b=bounds[1] ?: continue
            if(a !in 1..60 || b !in a..60) continue
            weeks += (a..b).filter { parity == -1 || it%2 == parity }
        } else if(bounds.size==1) bounds[0]?.takeIf { it in 1..60 }?.let(weeks::add)
    }
    return sourceIdForWeeks(weeks.toList(), includeNodes = false)
}

fun ScheduleProfile.visibleBase(base: List<CachedCourse>): List<CachedCourse> {
    val hidden=hiddenSourceIds.orEmpty().toSet()
    return if(hidden.isEmpty()) base else base.filterNot { it.sourceId() in hidden || it.sourceIdForWeeks(WeekUtil.parseWeeks(it.weeksRaw), includeNodes = false) in hidden || it.legacySourceId() in hidden }
}

fun ScheduleProfile.withCourseEdit(course: CustomCourse): ScheduleProfile = copy(
    customCourses = customCourses.filterNot { it.id == course.id } + course,
    hiddenSourceIds = hiddenSourceIds.orEmpty() + listOf(course.id).filter { it.startsWith("source:") },
)

fun ScheduleProfile.withCourseDeleted(id: String): ScheduleProfile = copy(
    customCourses = customCourses.filterNot { it.id == id },
    hiddenSourceIds = hiddenSourceIds.orEmpty() + listOf(id).filter { it.startsWith("source:") },
)
