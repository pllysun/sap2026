package edu.csuft.sap.data.local

import android.content.Context
import com.google.gson.Gson
import edu.csuft.sap.data.account.CurrentAccount
import edu.csuft.sap.data.remote.dto.EvalOverviewDto
import edu.csuft.sap.data.remote.dto.EvalRoundDto
import edu.csuft.sap.data.remote.dto.ExamDto
import edu.csuft.sap.data.remote.dto.GradeDto
import edu.csuft.sap.data.remote.dto.TermDto

/** 一次同步的结果 + 时间戳（epoch millis，0=从未同步）。 */
data class GradeCache(val items: List<GradeDto> = emptyList(), val syncedAt: Long = 0L)
data class ExamCache(val items: List<ExamDto> = emptyList(), val syncedAt: Long = 0L)
data class TermCache(val items: List<TermDto> = emptyList(), val syncedAt: Long = 0L)
data class EvalCache(val overview: EvalOverviewDto? = null, val syncedAt: Long = 0L)
data class EvalCatalogCache(
    val rounds: List<EvalRoundDto> = emptyList(),
    val selectedRoundId: Long? = null,
)

private data class JwCacheRoot(
    val grades: Map<String, GradeCache> = emptyMap(),
    val terms: Map<String, TermCache> = emptyMap(),
    val exams: Map<String, ExamCache> = emptyMap(),
    val eval: Map<String, EvalCache> = emptyMap(),
    val evalCatalog: Map<String, EvalCatalogCache> = emptyMap(),
)

/**
 * 成绩 / 考试 / 评教的本地缓存（SharedPreferences + Gson）。
 *
 * 每项缓存均按「登录会员账号 + 已绑定教务账号 + 学期/评教任务」隔离，绝不复用另一个
 * 会员账号的记录。v2 从干净命名空间开始，不读取旧版仅按教务学号保存的缓存，以杜绝升级后串数据。
 * 进页面只读缓存、不打教务；刷新由用户手动「同步」触发，避免大量人员频繁请求触发风控。
 * save* 写入即盖上当前时间戳并返回，VM 用它显示「上次同步」。
 */
class JwCacheStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("sap_jw_cache", Context.MODE_PRIVATE)
    private val gson = Gson()
    private var root: JwCacheRoot = load()

    fun grades(account: String): GradeCache? = root.grades[accountKey(account)]

    fun saveGrades(account: String, items: List<GradeDto>): Long {
        val at = System.currentTimeMillis()
        root = root.copy(grades = root.grades + (accountKey(account) to GradeCache(items, at)))
        persist()
        return at
    }

    fun terms(account: String): TermCache? = root.terms[accountKey(account)]

    fun saveTerms(account: String, items: List<TermDto>): Long {
        val at = System.currentTimeMillis()
        root = root.copy(terms = root.terms + (accountKey(account) to TermCache(items, at)))
        persist()
        return at
    }

    fun exams(account: String, term: String?): ExamCache? = root.exams[termKey(account, term)]

    fun saveExams(account: String, term: String?, items: List<ExamDto>): Long {
        val at = System.currentTimeMillis()
        root = root.copy(exams = root.exams + (termKey(account, term) to ExamCache(items, at)))
        persist()
        return at
    }

    /** 已缓存的评教任务目录，包含可切换的学年/评教轮次。 */
    fun evalCatalog(account: String): EvalCatalogCache? = root.evalCatalog[accountKey(account)]

    /** 仅记住用户上次查看的评教轮次，不会请求或覆盖任何评教内容。 */
    fun selectEvalRound(account: String, roundId: Long?) {
        val key = accountKey(account)
        val catalog = root.evalCatalog[key] ?: return
        if (catalog.selectedRoundId == roundId) return
        root = root.copy(evalCatalog = root.evalCatalog + (key to catalog.copy(selectedRoundId = roundId)))
        persist()
    }

    /** 读取一个评教任务的缓存；roundId=null 代表已同步但当前没有任何评教。 */
    fun eval(account: String, roundId: Long?): EvalCache? = root.eval[evalKey(account, roundId)]

    /**
     * 保存当前评教任务和完整轮次目录。请求成功但没有任何任务时，也会以 roundId=null 记录空结果。
     */
    fun saveEval(account: String, roundId: Long?, overview: EvalOverviewDto): Long {
        val at = System.currentTimeMillis()
        val effectiveRoundId = overview.taskId ?: roundId
        root = root.copy(
            eval = root.eval + (evalKey(account, effectiveRoundId) to EvalCache(overview, at)),
            evalCatalog = root.evalCatalog + (
                accountKey(account) to EvalCatalogCache(overview.rounds, effectiveRoundId)
            ),
        )
        persist()
        return at
    }

    private fun accountKey(jwAccount: String): String = "${CurrentAccount.key}$SEPARATOR${jwAccount.trim()}"

    private fun termKey(jwAccount: String, term: String?): String =
        "${accountKey(jwAccount)}$SEPARATOR${term.orEmpty()}"

    private fun evalKey(jwAccount: String, roundId: Long?): String =
        "${accountKey(jwAccount)}$SEPARATOR${roundId?.toString().orEmpty()}"

    private fun persist() {
        prefs.edit().putString(KEY, gson.toJson(root)).apply()
    }

    private fun load(): JwCacheRoot = try {
        prefs.getString(KEY, null)?.let { gson.fromJson(it, JwCacheRoot::class.java) } ?: JwCacheRoot()
    } catch (_: Exception) {
        JwCacheRoot()
    }

    private companion object {
        const val KEY = "jw_cache_v2"
        const val SEPARATOR = "\u001F"
    }
}
