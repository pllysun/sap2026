package edu.csuft.sap.ui.grade

import edu.csuft.sap.data.remote.dto.GradeDto

/**
 * 成绩派生统计：把原始 [GradeDto] 列表归约成可直接渲染的分析模型。
 * 纯函数、不依赖 Android/Compose，便于复用与单测。
 *
 * 数据约定（见后端 GradeParser）：
 * - [GradeDto.score] 可能是数字（"87"）也可能是等级（"优秀/良好/中等/及格/不及格"）；只对可解析为数字的算分数统计。
 * - [GradeDto.gradePoint]/[GradeDto.credit] 为数字字符串，可能为空。
 * - [GradeDto.term] 形如 "2023-2024-1"，可空（空归为"其它"）。
 */

/** 分数段：含下界、不含上界（满分 100 归入最后一段）。 */
enum class ScoreBand(val label: String, val min: Double, val max: Double) {
    FAIL("<60", 0.0, 60.0),
    D("60-69", 60.0, 70.0),
    C("70-79", 70.0, 80.0),
    B("80-89", 80.0, 90.0),
    A("90-100", 90.0, 100.1),
}

/** 一个学期的 GPA / 均分趋势点。 */
data class TermPoint(
    val term: String,
    val gpa: Double,        // 加权绩点
    val avgScore: Double,   // 数字成绩的加权均分（无数字成绩则 0）
    val courses: Int,
)

/** 课程性质 / 属性占比（按学分汇总）。 */
data class CreditSlice(
    val label: String,
    val credit: Double,
    val courses: Int,
)

/** 成绩分析总模型。 */
data class GradeStats(
    val totalCourses: Int = 0,
    val totalCredit: Double = 0.0,        // 全部课程学分之和
    val earnedCredit: Double = 0.0,       // 已修（及格/有绩点）学分之和
    val gpa: Double = 0.0,                // 学分加权平均绩点
    val avgScore: Double = 0.0,           // 数字成绩学分加权均分
    val scoredCourses: Int = 0,           // 有数字成绩的门数
    val maxScore: Double? = null,
    val minScore: Double? = null,
    val maxCourse: String? = null,
    val minCourse: String? = null,
    val failCount: Int = 0,               // 不及格门数
    val excellentCount: Int = 0,          // 优秀(>=90)门数
    val bands: List<Pair<ScoreBand, Int>> = emptyList(),
    val terms: List<TermPoint> = emptyList(),       // 按学期升序
    val attrSlices: List<CreditSlice> = emptyList(), // 课程属性（必修/选修…）按学分
) {
    val hasData: Boolean get() = totalCourses > 0
    val hasScored: Boolean get() = scoredCourses > 0
    val passRate: Double get() = if (scoredCourses > 0) (scoredCourses - failCount) * 100.0 / scoredCourses else 0.0
    val excellentRate: Double get() = if (scoredCourses > 0) excellentCount * 100.0 / scoredCourses else 0.0
}

/** 把分数字符串解析成数字；等级成绩（优秀/不及格…）返回 null。 */
fun parseScore(score: String?): Double? = score?.trim()?.toDoubleOrNull()

/** 是否不及格：数字 <60，或等级文案含"不及格/不合格"。 */
fun isFailScore(score: String?): Boolean {
    if (score == null) return false
    parseScore(score)?.let { return it < 60.0 }
    return score.contains("不及格") || score.contains("不合格")
}

private fun GradeDto.creditValue(): Double = credit?.trim()?.toDoubleOrNull() ?: 0.0
private fun GradeDto.gpValue(): Double? = gradePoint?.trim()?.toDoubleOrNull()

/** 由成绩列表计算完整分析模型。 */
fun computeGradeStats(grades: List<GradeDto>): GradeStats {
    if (grades.isEmpty()) return GradeStats()

    val totalCredit = grades.sumOf { it.creditValue() }

    // 学分加权 GPA：仅取有正学分且有绩点的课程。
    val gpaItems = grades.mapNotNull { g ->
        val c = g.creditValue(); val gp = g.gpValue()
        if (c > 0 && gp != null) c to gp else null
    }
    val gpa = if (gpaItems.isNotEmpty())
        gpaItems.sumOf { it.first * it.second } / gpaItems.sumOf { it.first } else 0.0

    // 已修学分：及格（未挂科）的学分之和。
    val earnedCredit = grades.filter { !isFailScore(it.score) }.sumOf { it.creditValue() }

    // 数字成绩相关：均分（学分加权）、最高/最低、分布、优秀/挂科。
    val scored = grades.mapNotNull { g -> parseScore(g.score)?.let { g to it } }
    val avgScore = run {
        val wItems = scored.filter { it.first.creditValue() > 0 }
        if (wItems.isNotEmpty())
            wItems.sumOf { it.first.creditValue() * it.second } / wItems.sumOf { it.first.creditValue() }
        else scored.map { it.second }.average().takeIf { scored.isNotEmpty() } ?: 0.0
    }
    val maxEntry = scored.maxByOrNull { it.second }
    val minEntry = scored.minByOrNull { it.second }
    val failCount = grades.count { isFailScore(it.score) }
    val excellentCount = scored.count { it.second >= 90.0 }

    val bands = ScoreBand.entries.map { band ->
        band to scored.count { (_, s) -> s >= band.min && s < band.max }
    }

    // 按学期聚合（升序）。
    val terms = grades.groupBy { it.term?.takeIf { t -> t.isNotBlank() } ?: "其它" }
        .toList()
        .sortedBy { it.first }
        .map { (term, list) ->
            val gp = list.mapNotNull { g -> val c = g.creditValue(); val v = g.gpValue(); if (c > 0 && v != null) c to v else null }
            val termGpa = if (gp.isNotEmpty()) gp.sumOf { it.first * it.second } / gp.sumOf { it.first } else 0.0
            val sc = list.mapNotNull { g -> parseScore(g.score)?.let { c -> g.creditValue() to c } }
            val termAvg = run {
                val w = sc.filter { it.first > 0 }
                if (w.isNotEmpty()) w.sumOf { it.first * it.second } / w.sumOf { it.first }
                else sc.map { it.second }.average().takeIf { sc.isNotEmpty() } ?: 0.0
            }
            TermPoint(term = term, gpa = termGpa, avgScore = termAvg, courses = list.size)
        }

    // 课程属性占比（按学分）：必修/选修…；空属性归"其它"。
    val attrSlices = grades.groupBy { it.courseAttr?.takeIf { a -> a.isNotBlank() } ?: "其它" }
        .map { (label, list) -> CreditSlice(label, list.sumOf { it.creditValue() }, list.size) }
        .sortedByDescending { it.credit }

    return GradeStats(
        totalCourses = grades.size,
        totalCredit = totalCredit,
        earnedCredit = earnedCredit,
        gpa = gpa,
        avgScore = avgScore,
        scoredCourses = scored.size,
        maxScore = maxEntry?.second,
        minScore = minEntry?.second,
        maxCourse = maxEntry?.first?.courseName,
        minCourse = minEntry?.first?.courseName,
        failCount = failCount,
        excellentCount = excellentCount,
        bands = bands,
        terms = terms,
        attrSlices = attrSlices,
    )
}
