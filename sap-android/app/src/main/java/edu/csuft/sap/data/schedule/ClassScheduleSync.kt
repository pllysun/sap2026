package edu.csuft.sap.data.schedule

import android.util.Log
import edu.csuft.sap.data.account.*
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.dto.*
import edu.csuft.sap.data.repository.ClassScheduleRepository
import kotlinx.coroutines.*

/** 应用级单任务：前台进入/模式切换合并触发，失败保留全部缓存，下次进入再检测。 */
class ClassScheduleSync(private val repository: ClassScheduleRepository, private val store: ScheduleStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var runningOwner: String? = null

    fun request() {
        val owner = CurrentAccount.key
        if (!allowed(owner)) return
        if (job?.isActive == true && runningOwner == owner) return
        job?.cancel()
        runningOwner = owner
        job = scope.launch {
            try { refresh(owner) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { Log.w("ClassScheduleSync", "后台校验未完成，保留本地缓存") }
        }
    }

    private fun allowed(owner: String) = owner != "_" && CurrentAccount.key == owner &&
        ConnectivityState.online && MemberState.isClass

    private suspend fun refresh(owner: String) {
        val slots = store.cachedClassAccounts().map { (account, _) -> account to store.accountData(account) }
        val pending = mutableListOf<Pair<ClassSyncSelection, ClassRefresh>>()
        for ((account, data) in slots) {
            for (profile in data.profiles.filter { it.kind == ProfileKind.TERM && it.termValue != null }) {
                ensureAllowed(owner)
                val term = profile.termValue!!
                val identity = data.classIdentity ?: withContext(Dispatchers.IO) {
                    resolveLegacy(account, term, profile.name)
                } ?: return
                val key = "${identity.key()}:$term"
                pending += ClassSyncSelection(key, term, identity.college, identity.grade, identity.major,
                    identity.className, data.classRevisions?.get(term)) to
                    ClassRefresh(account, profile.id, term, identity, data.termCourses[term].orEmpty(), emptyList(), null, "")
            }
        }
        if (pending.isEmpty()) return
        // 超过 100 份也顺序分批，不并发。如果任一批改变，再补取其他批，最后统一提交。
        val items = fetchClassSyncBatch(pending.map { it.first }) { request ->
            ensureAllowed(owner)
            repository.sync(request)
        } ?: return
        ensureAllowed(owner)
        val byKey = items.associateBy { it.key }
        val updates = pending.map { (selection, previous) ->
            val item = byKey[selection.key] ?: return
            val data = item.data ?: return
            if (data.term != previous.term || !item.revision.matches(Regex("[a-f0-9]{64}"))) return
            previous.copy(courses = data.courses.map {
                CachedCourse(it.name.orEmpty(), it.teacher.orEmpty(), it.room.orEmpty(), it.day,
                    it.sectionIndex.coerceAtLeast(1), it.weeks, edu.csuft.sap.ui.theme.colorIndexOf(it.name))
            }, startDate = data.semesterStartDate, revision = item.revision)
        }
        store.applyClassRefresh(owner, updates) { allowed(owner) }
    }

    private suspend fun ensureAllowed(owner: String) {
        currentCoroutineContext().ensureActive()
        if (!allowed(owner)) throw CancellationException("课表模式或账号已切换")
    }

    /** 旧缓存只有摘要键；必须校验完整班级身份，不能按班名模糊匹配。 */
    private suspend fun resolveLegacy(account: String, term: String, name: String): ClassIdentity? {
        fun List<ClassOptionDto>.findIdentity() = map {
            ClassIdentity(it.college.orEmpty(), it.grade.orEmpty(), it.major.orEmpty(), it.className.orEmpty())
        }.firstOrNull { it.account() == account }
        repository.cachedClasses(term).findIdentity()?.let { return it }
        name.split(" · ").takeIf { it.size == 4 }?.let {
            ClassIdentity(it[0], it[1], it[2], it[3]).takeIf { candidate -> candidate.account() == account }?.let { return it }
        }
        return when (val result = repository.classes(term, null, null, force = true)) {
            is Outcome.Success -> result.data.findIdentity()
            is Outcome.Error -> null
        }
    }
}
