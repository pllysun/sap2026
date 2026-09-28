package edu.csuft.sap.data.schedule

import edu.csuft.sap.data.account.AccountManager
import java.security.MessageDigest
import java.util.Base64

data class ClassIdentity(val college: String, val grade: String, val major: String, val className: String) {
    fun key(): String {
        val raw = listOf(college, grade, major, className).joinToString("\u001F")
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))).take(22)
    }
    fun account() = AccountManager.CLASS_ACCOUNT_PREFIX + key()
}

data class ClassRefresh(
    val account: String, val profileId: String, val term: String, val identity: ClassIdentity,
    val previousCourses: List<CachedCourse>, val courses: List<CachedCourse>,
    val startDate: String?, val revision: String,
)

/** 合并到最新状态，不改变当前课表、手动日期、自建课程；不复活下载期间删除的缓存。 */
fun ScheduleRoot.withClassRefresh(owner: String, updates: List<ClassRefresh>): ScheduleRoot {
    val updated = accounts.toMutableMap()
    for (u in updates) {
        val key = ScheduleStore.accountStorageKey(owner, u.account)
        val data = updated[key] ?: continue
        val profile = data.profiles.firstOrNull {
            it.id == u.profileId && it.kind == ProfileKind.TERM && it.termValue == u.term
        } ?: continue
        // 期间手动重新下载过该学期，保留用户刚获取的版本，下一次再探测。
        if (data.termCourses[u.term].orEmpty() != u.previousCourses) continue
        val settings = if (profile.settings.semesterStartDateManual || u.startDate.isNullOrBlank()) profile.settings
            else profile.settings.copy(semesterStartDate = u.startDate)
        updated[key] = data.copy(
            classIdentity = u.identity,
            classRevisions = data.classRevisions.orEmpty() + (u.term to u.revision),
            termCourses = data.termCourses + (u.term to u.courses),
            profiles = data.profiles.map { if (it.id == profile.id) it.copy(settings = settings) else it },
        )
    }
    return copy(accounts = updated)
}
