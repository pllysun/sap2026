package edu.csuft.sap.data.schedule

import android.content.Context
import com.google.gson.Gson
import edu.csuft.sap.data.account.AccountManager
import edu.csuft.sap.data.account.CurrentAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 多账户·多课表的本地存储（SharedPreferences + Gson）。
 * 课表内容按「登录会员账号 + 教务学号」隔离，课表显示配置则是设备级全局配置；
 * 整棵 [ScheduleRoot] 序列化为一条 JSON，对外暴露 [root] StateFlow，全部写操作走不可变 mutate 后持久化。
 */
class ScheduleStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext
        .getSharedPreferences("sap_schedule", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _root = MutableStateFlow(load())
    val root: StateFlow<ScheduleRoot> = _root.asStateFlow()

    fun applyAcademicCalendar(dates: Map<String, String>) {
        val current = _root.value
        val updated = current.withAcademicCalendar(dates)
        if (updated != current) persist(updated)
    }

    fun accountData(account: String): AccountData = _root.value.accounts[storageAccountKey(account)] ?: AccountData()

    fun finishScheduleSync(account: String, terms: Set<String>) {
        val before = _root.value
        val after = before.withSyncedWeekend(storageAccountKey(account), terms)
        if (after != before) persist(after)
    }

    /** JSON 编码放后台；提交前校验账号和根版本，避免覆盖刷新期间的界面编辑。 */
    suspend fun applyClassRefresh(owner: String, updates: List<ClassRefresh>, allowed: () -> Boolean) {
        while (CurrentAccount.key == owner && allowed()) {
            val before = _root.value
            val accountPrefs = appContext.getSharedPreferences("sap_account", Context.MODE_PRIVATE)
            val active = accountPrefs.getString("active_account", null)
            val (after, json) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val refreshed = before.withClassRefresh(owner, updates)
                val terms = updates.filter { it.account == active &&
                    before.accounts[accountStorageKey(owner, it.account)]?.termCourses?.get(it.term) == it.previousCourses
                }.mapTo(mutableSetOf()) { it.term }
                val next = if (active != null) refreshed.withSyncedWeekend(accountStorageKey(owner, active), terms) else refreshed
                next to gson.toJson(next)
            }
            val committed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                if (CurrentAccount.key != owner || !allowed()) return@withContext true
                if (_root.value !== before || accountPrefs.getString("active_account", null) != active) false else {
                    if (after != before) {
                        prefs.edit().putString(KEY_ROOT, json).apply()
                        _root.value = after
                    }
                    true
                }
            }
            if (committed) return
        }
    }

    /** 已下载的班级缓存槽；用于离线打开选择器并切换历史班级。 */
    fun cachedClassAccounts(): List<Pair<String, String>> {
        val prefix = accountStorageKey(CurrentAccount.key, AccountManager.CLASS_ACCOUNT_PREFIX)
        return _root.value.accounts
            .filterKeys { it.startsWith(prefix) }
            .mapNotNull { (stored, data) ->
                val account = stored.removePrefix("${CurrentAccount.key}\u001F")
                val name = data.profiles.firstOrNull()?.name ?: return@mapNotNull null
                account to name
            }
            .sortedBy { it.second }
    }

    /**
     * 把当前教务账号的本地课表缓存继承到 Web 本地空间。
     *
     * 云控从完整能力降为基础能力时，界面会切到 [AccountManager.WEBVIEW_ACCOUNT]。教务缓存与
     * Web 缓存原本按数据源隔离，因此切换前必须做一次本地快照交接，否则用户会误以为课表丢失。
     * 这里只复制设备上的缓存：不解绑教务账号、不删除源数据，也不发起任何网络请求。
     */
    fun inheritIntoWebview(sourceAccount: String): Boolean {
        if (sourceAccount.isBlank() || AccountManager.isLocalOrClass(sourceAccount)) return false
        val current = _root.value
        val source = current.accounts[storageAccountKey(sourceAccount)] ?: return false
        if (source.profiles.isEmpty()) return false
        val targetKey = storageAccountKey(AccountManager.WEBVIEW_ACCOUNT)
        val target = current.accounts[targetKey] ?: AccountData()
        val inherited = inheritScheduleSnapshot(source, target)
        if (inherited != target) {
            val accounts = current.accounts.toMutableMap().apply { put(targetKey, inherited) }
            persist(current.copy(accounts = accounts))
        }
        return true
    }

    /**
     * 返回带设备级显示配置的课表设置。
     * 升级旧数据时以用户当前打开的课表为迁移来源，避免换版本后丢失既有个性化配置。
     */
    fun effectiveSettings(settings: ScheduleSettings): ScheduleSettings {
        val current = _root.value
        val display = current.displaySettings ?: settings.toDisplaySettings().copy(
            // 这两个选项随本次设备级配置上线；旧 JSON 中缺字段会被 Gson 解成 false，迁移时使用新默认值。
            centerTextHorizontally = true,
            centerTextVertically = true,
        ).also {
            persist(current.withDisplaySettings(it))
        }
        return settings.withDisplaySettings(display)
    }

    // ---- 学号级 ----

    fun setTermCourses(account: String, term: String, courses: List<CachedCourse>) =
        mutate(account) { it.copy(termCourses = it.termCourses + (term to courses)) }

    fun setTermRemarks(account: String, term: String, remarks: List<Remark>) =
        mutate(account) { it.copy(termRemarks = (it.termRemarks ?: emptyMap()) + (term to remarks)) }

    fun setScanned(account: String, scanned: Boolean) =
        mutate(account) { it.copy(scanned = scanned) }

    fun setActiveProfile(account: String, profileId: String) =
        mutate(account) { it.copy(activeProfileId = profileId) }

    fun addProfile(account: String, profile: ScheduleProfile, makeActive: Boolean) =
        mutate(account) {
            it.copy(
                profiles = it.profiles + profile,
                activeProfileId = if (makeActive) profile.id else it.activeProfileId,
            )
        }

    /** 用扫描出的学期课表整体重建 TERM 课表，保留已存在课表的自建课/设置；默认选中首个。 */
    fun replaceTermProfiles(account: String, profiles: List<ScheduleProfile>) =
        mutate(account) { data ->
            val customProfiles = data.profiles.filter { it.kind == ProfileKind.CUSTOM }
            val merged = profiles.map { fresh ->
                val old = data.profiles.firstOrNull {
                    it.kind == ProfileKind.TERM && it.termValue == fresh.termValue
                }
                if (old != null) fresh.copy(
                    name = old.name,
                    settings = old.settings,
                    customCourses = old.customCourses,
                    hiddenSourceIds = old.hiddenSourceIds,
                ) else fresh
            }
            val all = merged + customProfiles
            val active = data.activeProfileId?.takeIf { id -> all.any { it.id == id } }
                ?: merged.firstOrNull()?.id
            data.copy(profiles = all, activeProfileId = active, scanned = true)
        }

    /**
     * WebView 端上导入某学期课表，写入指定账号（通常本地网页源），并置为当前、scanned=true。
     * - [replaceAll]=true（非会员）：**单一课表覆盖式**——只保留这一份，丢弃其它学期/profile；
     *   同学期重导保留学期设置与自建课，显示偏好始终使用设备级全局配置。
     * - [replaceAll]=false（会员）：**多课表**——新增/更新该学期 TERM 课表，保留其它课表/自建课/另存为副本，
     *   与教务多学期一致，可切换/另存为/重命名。
     */
    fun importWebview(
        account: String,
        term: String,
        courses: List<CachedCourse>,
        remarks: List<Remark>,
        replaceAll: Boolean,
    ) = mutate(account) { data ->
        if (replaceAll) {
            val sameTerm = data.profiles.firstOrNull { it.termValue == term }
            val profile = ScheduleProfile(
                id = "webview:$term",
                name = termLabel(term),
                kind = ProfileKind.TERM,
                termValue = term,
                settings = sameTerm?.settings ?: ScheduleSettings(),
                customCourses = sameTerm?.customCourses ?: emptyList(),
                hiddenSourceIds = sameTerm?.hiddenSourceIds,
            )
            data.copy(
                termCourses = mapOf(term to courses),   // 覆盖：丢弃其它学期
                termRemarks = mapOf(term to remarks),   // 覆盖
                profiles = listOf(profile),             // 课表唯一，丢弃旧 profiles
                activeProfileId = profile.id,
                scanned = true,
            )
        } else {
            // 会员：合并入库，保留其它学期/自建课/副本
            val existing = data.profiles.firstOrNull { it.kind == ProfileKind.TERM && it.termValue == term }
            val profile = ScheduleProfile(
                id = existing?.id ?: "webview:$term",
                name = existing?.name ?: termLabel(term),
                kind = ProfileKind.TERM,
                termValue = term,
                settings = existing?.settings ?: ScheduleSettings(),
                customCourses = existing?.customCourses ?: emptyList(),
                hiddenSourceIds = existing?.hiddenSourceIds,
            )
            data.copy(
                termCourses = data.termCourses + (term to courses),
                termRemarks = (data.termRemarks ?: emptyMap()) + (term to remarks),
                profiles = data.profiles.filterNot { it.id == profile.id } + profile,
                activeProfileId = profile.id,
                scanned = true,
            )
        }
    }

    /**
     * 单槽导入：没有课表时创建一份；已有课表时只替换当前课表的教务底本，
     * 其它历史课表继续保留且不会因导入而新增。设备级外观和当前课表的自建内容均保留。
     */
    fun importWebviewSingleSlot(
        account: String,
        term: String,
        courses: List<CachedCourse>,
        remarks: List<Remark>,
    ) = mutate(account) { data ->
        val target = data.profiles.firstOrNull { it.id == data.activeProfileId }
            ?: data.profiles.firstOrNull()
        val profile = if (target == null) {
            ScheduleProfile(
                id = "webview:$term",
                name = termLabel(term),
                kind = ProfileKind.TERM,
                termValue = term,
            )
        } else {
            target.copy(
                name = termLabel(term),
                kind = ProfileKind.TERM,
                termValue = term,
                frozenCourses = emptyList(),
            )
        }
        data.copy(
            termCourses = data.termCourses + (term to courses),
            termRemarks = (data.termRemarks ?: emptyMap()) + (term to remarks),
            profiles = if (target == null) listOf(profile)
                else data.profiles.map { if (it.id == target.id) profile else it },
            activeProfileId = profile.id,
            scanned = true,
        )
    }

    /** 写入一个班级缓存槽。account 通常是 AccountManager.CLASS_ACCOUNT_PREFIX + 选择摘要，
     * 因而不同班级互不覆盖，可在无网时直接切换已经下载过的槽。 */
    fun importClass(
        account: String,
        term: String,
        className: String,
        courses: List<CachedCourse>,
        startDate: String?,
        displayName: String = className,
        identity: ClassIdentity? = null,
    ) = mutate(account) { data ->
        val old = data.profiles.firstOrNull { it.kind == ProfileKind.TERM && it.termValue == term }
        val settings = (old?.settings ?: ScheduleSettings()).let {
            if (it.semesterStartDateManual || startDate.isNullOrBlank()) it
            else it.copy(semesterStartDate = startDate, semesterStartDateManual = false)
        }
        val profile = ScheduleProfile(
            id = old?.id ?: "class:$term",
            name = displayName.ifBlank { className.ifBlank { termLabel(term) } },
            kind = ProfileKind.TERM,
            termValue = term,
            settings = settings,
            customCourses = old?.customCourses ?: emptyList(),
            hiddenSourceIds = old?.hiddenSourceIds,
        )
        data.copy(
            classIdentity = identity ?: data.classIdentity,
            classRevisions = data.classRevisions.orEmpty() - term,
            termCourses = data.termCourses + (term to courses),
            profiles = data.profiles.filterNot { it.id == profile.id } + profile,
            activeProfileId = profile.id,
            scanned = true,
        )
    }

    /** 自动开学日期写到对应学期课表（不覆盖用户手动设过的）。供网页登录导入后回填教学周历开学日。 */
    fun setSemesterStart(account: String, term: String, startIso: String) =
        mutate(account) { data ->
            val p = data.profiles.firstOrNull { it.kind == ProfileKind.TERM && it.termValue == term }
                ?: return@mutate data
            if (p.settings.semesterStartDateManual || p.settings.semesterStartDate == startIso) return@mutate data
            data.copy(profiles = data.profiles.map {
                if (it.id == p.id) it.copy(settings = it.settings.copy(semesterStartDate = startIso, semesterStartDateManual = false))
                else it
            })
        }

    private fun termLabel(term: String): String {
        // 2025-2026-2 → 2025-2026 第2学期
        val m = Regex("(\\d{4})-(\\d{4})-(\\d)").find(term) ?: return term
        return "${m.groupValues[1]}-${m.groupValues[2]} 第${m.groupValues[3]}学期"
    }

    fun removeProfile(account: String, profileId: String) {
        mutate(account) { d ->
            val rest = d.profiles.filterNot { it.id == profileId }
            d.copy(
                profiles = rest,
                activeProfileId = if (d.activeProfileId == profileId) rest.firstOrNull()?.id
                else d.activeProfileId,
            )
        }
    }

    /** 清空一个本地来源账号及其所有学期数据。用于删除班级课表缓存时避免残留旧课表。 */
    fun clearAccount(account: String) {
        val cur = _root.value
        val key = storageAccountKey(account)
        if (!cur.accounts.containsKey(key)) return
        val accounts = cur.accounts.toMutableMap().apply { remove(key) }
        persist(cur.copy(accounts = accounts))
    }

    /** 一次写入移除所选班级的全部学期、设置、备注和自建课程。 */
    fun clearClassAccounts(accounts: Set<String>) {
        val current = _root.value
        val updated = current.withoutClassAccounts(CurrentAccount.key, accounts)
        if (updated != current) persist(updated)
    }

    // ---- 课表级 ----

    fun renameProfile(account: String, profileId: String, name: String) =
        mutateProfile(account, profileId) { it.copy(name = name.trim()) }

    fun updateSettings(account: String, profileId: String, settings: ScheduleSettings) {
        val cur = _root.value
        val key = storageAccountKey(account)
        val data = cur.accounts[key] ?: AccountData()
        val updatedData = data.copy(
            profiles = data.profiles.map { profile ->
                if (profile.id == profileId) profile.copy(settings = settings) else profile
            },
        )
        val accounts = cur.accounts.toMutableMap().apply { put(key, updatedData) }
        persist(
            cur.copy(
                accounts = accounts,
                displaySettings = settings.toDisplaySettings(),
            ),
        )
    }

    fun upsertCourse(account: String, profileId: String, course: CustomCourse) =
        mutateProfile(account, profileId) { p -> p.withCourseEdit(course) }

    fun deleteCourse(account: String, profileId: String, courseId: String) =
        mutateProfile(account, profileId) { p ->
            p.withCourseDeleted(courseId)
        }

    // ---- 内部 ----

    private inline fun mutate(account: String, block: (AccountData) -> AccountData) {
        val cur = _root.value
        val key = storageAccountKey(account)
        val data = cur.accounts[key] ?: AccountData()
        val next = cur.accounts.toMutableMap().apply { put(key, block(data)) }
        persist(cur.copy(accounts = next))
    }

    private inline fun mutateProfile(
        account: String,
        profileId: String,
        crossinline block: (ScheduleProfile) -> ScheduleProfile,
    ) = mutate(account) { d ->
        d.copy(profiles = d.profiles.map { if (it.id == profileId) block(it) else it })
    }

    private fun persist(root: ScheduleRoot) {
        val normalized = root.displaySettings?.let(root::withDisplaySettings) ?: root
        prefs.edit().putString(KEY_ROOT, gson.toJson(normalized)).apply()
        _root.value = normalized
    }

    private fun load(): ScheduleRoot {
        val json = prefs.getString(KEY_ROOT, null) ?: return ScheduleRoot()
        return try {
            val parsed = gson.fromJson(json, ScheduleRoot::class.java) ?: ScheduleRoot()
            parsed.displaySettings?.let(parsed::withDisplaySettings) ?: parsed
        } catch (_: Exception) {
            ScheduleRoot()
        }
    }

    private fun storageAccountKey(jwAccount: String): String = accountStorageKey(CurrentAccount.key, jwAccount)

    companion object {
        private const val KEY_ROOT = "root_v4"

        /** 供小组件进程按当前会员账号 + 激活教务账号读取同一份课表缓存。 */
        fun accountStorageKey(memberAccount: String, jwAccount: String): String =
            "$memberAccount\u001F${jwAccount.trim()}"
    }
}

internal fun ScheduleRoot.withoutClassAccounts(member: String, selected: Set<String>): ScheduleRoot {
    val keys = selected.filter(AccountManager::isClass)
        .mapTo(HashSet()) { ScheduleStore.accountStorageKey(member, it) }
    return copy(accounts = accounts.filterKeys { it !in keys })
}

/**
 * 合并一次“教务缓存 → Web 本地空间”的降级快照。
 *
 * Web 空间为空时完整复制；已经有 Web 课表时保留其同名课表与内容，只补入缺少的课表和学期数据，
 * 并尽量延续教务侧当前选中的课表。该函数无 Android 依赖，便于用单元测试锁定数据不丢失语义。
 */
internal fun inheritScheduleSnapshot(source: AccountData, target: AccountData): AccountData {
    if (source.profiles.isEmpty()) return target
    if (target.profiles.isEmpty()) return source.copy(scanned = true)

    val targetIds = target.profiles.mapTo(HashSet()) { it.id }
    val profiles = target.profiles + source.profiles.filterNot { it.id in targetIds }
    val active = source.activeProfileId?.takeIf { id -> profiles.any { it.id == id } }
        ?: target.activeProfileId?.takeIf { id -> profiles.any { it.id == id } }
        ?: profiles.firstOrNull()?.id
    val remarks = when {
        source.termRemarks == null && target.termRemarks == null -> null
        else -> source.termRemarks.orEmpty() + target.termRemarks.orEmpty()
    }
    return target.copy(
        activeProfileId = active,
        profiles = profiles,
        // 已有 Web 同学期内容优先，避免继承动作覆盖用户此前手动导入的数据。
        termCourses = source.termCourses + target.termCourses,
        termRemarks = remarks,
        scanned = true,
    )
}
