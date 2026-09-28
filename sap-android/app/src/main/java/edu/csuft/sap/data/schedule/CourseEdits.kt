package edu.csuft.sap.data.schedule

import java.security.MessageDigest

/** 不依赖返回顺序或数据库行 ID，重新拉取同一底本后仍保留本地编辑。 */
fun CachedCourse.sourceId(): String {
    val fields = listOf(name, teacher, location, day.toString(), sectionIndex.toString(),
        WeekUtil.parseWeeks(weeksRaw).sorted().joinToString(","))
    val canonical = fields.joinToString("") { "${it.length}:$it" }
    return "source:" + MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

fun ScheduleProfile.visibleBase(base: List<CachedCourse>) =
    base.filterNot { it.sourceId() in hiddenSourceIds.orEmpty() }

fun ScheduleProfile.withCourseEdit(course: CustomCourse): ScheduleProfile = copy(
    customCourses = customCourses.filterNot { it.id == course.id } + course,
    hiddenSourceIds = hiddenSourceIds.orEmpty() + listOf(course.id).filter { it.startsWith("source:") },
)

fun ScheduleProfile.withCourseDeleted(id: String): ScheduleProfile = copy(
    customCourses = customCourses.filterNot { it.id == id },
    hiddenSourceIds = hiddenSourceIds.orEmpty() + listOf(id).filter { it.startsWith("source:") },
)
