package edu.csuft.sap.data.schedule

import java.text.Normalizer

/** 实际小节与旧版大节索引分开保存，支持连续实验课和不连续的小节列表。 */
object SchoolSections {
    fun parse(raw: String?): List<Int> {
        if (raw.isNullOrBlank() || raw.length > 128) return emptyList()
        val text = Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .replace(Regex("[第节\\s\\[\\]()【】]"), "")
            .replace(Regex("[—–~～至到]"), "-")
        val nodes = sortedSetOf<Int>()
        for (part in text.split(',', '、', ';', '；')) {
            if (!part.matches(Regex("\\d{1,2}(?:-\\d{1,2})?"))) return emptyList()
            val ends = part.split('-').map { it.toInt() }
            val start = ends.first(); val end = ends.last()
            if (start < 1 || end < start || end > Periods.MAX_NODES) return emptyList()
            nodes += start..end
        }
        return nodes.toList()
    }

    fun fromRow(section: String?, sectionIndex: Int): List<Int> =
        if (section.orEmpty().contains(Regex("[,，、\\-－—–~～至到]")))
            parse(section).ifEmpty { Periods.nodesOfSection(sectionIndex).toList() }
        else Periods.nodesOfSection(sectionIndex).toList()
}

fun CachedCourse.nodeRanges(): List<IntRange> {
    val actual = nodes?.takeIf { it.isNotEmpty() && it.all { n -> n in 1..Periods.MAX_NODES } }
        ?.distinct()?.sorted() ?: Periods.nodesOfSection(sectionIndex).toList()
    val ranges = mutableListOf<IntRange>()
    for (node in actual) {
        val last = ranges.lastOrNull()
        if (last != null && last.last + 1 == node) ranges[ranges.lastIndex] = last.first..node
        else ranges += node..node
    }
    return ranges
}

/** 旧 App 可能保存了新服务的指纹，却没有保存 nodes；此时仍需重新取课表。 */
fun cachedClassRevision(courses: List<CachedCourse>, revision: String?): String? =
    revision?.takeIf { courses.none { it.nodes == null } }
