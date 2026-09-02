package edu.csuft.sap.data.schedule

/** 学期号工具：格式 "YYYY-YYYY-D"（D=学期：1 秋/上、2 春/下、3 短/夏）。 */
object TermUtil {
    private val RE = Regex("^(\\d{4})-(\\d{4})-(\\d)$")

    fun isTerm(v: String): Boolean = RE.matches(v.trim())

    /** 学期尾号(1/2/3)；非法返回 0。 */
    fun semester(term: String): Int = RE.matchEntire(term.trim())?.groupValues?.get(3)?.toIntOrNull() ?: 0

    /** 下一学期：D=1→D=2(同学年)；否则进下一学年 D=1（按 2 学期/学年，跳过夏短学期）。 */
    fun next(term: String): String {
        val m = RE.matchEntire(term.trim()) ?: return term
        val y1 = m.groupValues[1].toInt(); val y2 = m.groupValues[2].toInt(); val d = m.groupValues[3].toInt()
        return if (d == 1) "$y1-$y2-2" else "${y1 + 1}-${y2 + 1}-1"
    }

    /** 上一学期：D>1→D=1(同学年)；D=1→上一学年 D=2。 */
    fun prev(term: String): String {
        val m = RE.matchEntire(term.trim()) ?: return term
        val y1 = m.groupValues[1].toInt(); val y2 = m.groupValues[2].toInt(); val d = m.groupValues[3].toInt()
        return if (d > 1) "$y1-$y2-1" else "${y1 - 1}-${y2 - 1}-2"
    }

    /** 2024-2025-2 → 2024-2025 第2学期；非法格式原样返回。 */
    fun label(term: String): String {
        val m = RE.matchEntire(term.trim()) ?: return term
        return "${m.groupValues[1]}-${m.groupValues[2]} 第${m.groupValues[3]}学期"
    }
}

/**
 * 统一的「有数据学期」扫描：以**当前学期**为锚，向过去与将来逐个推进，遇连续空学期或达上限即止。
 * 教务账号抓取与 WebView 抓取共用本逻辑——既不漏数据，也不无效抓取。
 *
 * 例：现在 2026-06，当前 2025-2026-2。
 * - 大一下：当前有课；向过去 大一上(有)→入学前(空)→再前(空,连续2空)止；向将来 2026-2027-1(若已发布有课)。
 * - 大四：当前(大四下)空但仍尝试；向过去 大四上(有)…大一上(有)，至连续 2 空或满 8 个止。
 */
object TermScan {
    const val BACK_CAP = 8         // 向过去最多 8 个学期（覆盖四年）
    const val FWD_CAP = 4          // 向将来最多 4 个学期（提前发布的下学期）
    const val MAX_EMPTY_STREAK = 2 // 连续 2 个空学期才停（避免大四下这类单个空学期误判为结束）

    /**
     * 以 [current] 为锚扫描。[hasData] 抓取并存储某学期、返回该学期是否有课。
     * 返回所有有课的学期（去重）。
     */
    suspend fun scanAround(
        current: String,
        backCap: Int = BACK_CAP,
        fwdCap: Int = FWD_CAP,
        maxEmptyStreak: Int = MAX_EMPTY_STREAK,
        hasData: suspend (term: String) -> Boolean,
    ): List<String> {
        val withData = LinkedHashSet<String>()
        if (hasData(current)) withData.add(current)
        // 向过去
        var streak = 0; var t = TermUtil.prev(current); var n = 0
        while (n < backCap && streak < maxEmptyStreak) {
            if (hasData(t)) { withData.add(t); streak = 0 } else streak += 1
            t = TermUtil.prev(t); n += 1
        }
        // 向将来
        streak = 0; t = TermUtil.next(current); n = 0
        while (n < fwdCap && streak < maxEmptyStreak) {
            if (hasData(t)) { withData.add(t); streak = 0 } else streak += 1
            t = TermUtil.next(t); n += 1
        }
        return withData.toList()
    }

    /**
     * 默认选中学期：
     * - 当前学期有课 → 默认当前；若当前是春季学期(尾号2)、已过 7 月([month]≥8)且下一学年课表已出 → 默认下一学期(秋季)。
     * - 当前学期无课(如大四下) → 最近的有课学期。
     */
    fun defaultTerm(current: String, withData: Collection<String>, month: Int): String? {
        if (withData.isEmpty()) return null
        if (current !in withData) return withData.maxOrNull()
        val nxt = TermUtil.next(current)
        if (TermUtil.semester(current) == 2 && month >= 8 && nxt in withData) return nxt
        return current
    }
}
