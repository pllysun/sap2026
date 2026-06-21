package edu.csuft.sap.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.dto.CourseDto
import edu.csuft.sap.data.remote.dto.RemarkDto
import edu.csuft.sap.data.remote.dto.ScheduleData
import edu.csuft.sap.data.remote.dto.TermDto
import edu.csuft.sap.data.account.AccountManager
import edu.csuft.sap.data.account.JwMfaState
import edu.csuft.sap.data.account.MemberState
import kotlinx.coroutines.flow.drop
import edu.csuft.sap.data.schedule.AccountData
import edu.csuft.sap.data.schedule.CachedCourse
import edu.csuft.sap.data.schedule.CustomCourse
import edu.csuft.sap.data.schedule.DisplayCourse
import edu.csuft.sap.data.schedule.Periods
import edu.csuft.sap.data.schedule.Remark
import edu.csuft.sap.data.schedule.ProfileKind
import edu.csuft.sap.data.schedule.ScheduleProfile
import edu.csuft.sap.data.schedule.ScheduleSettings
import edu.csuft.sap.data.schedule.TermScan
import edu.csuft.sap.data.schedule.TermUtil
import edu.csuft.sap.data.schedule.WeekUtil
import edu.csuft.sap.di.Graph
import edu.csuft.sap.notify.ReminderScheduler
import edu.csuft.sap.ui.theme.colorIndexOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 课表页 VM：按激活学号取本地多课表，TERM 课表底本来自教务（自动扫描有数据学期），
 * CUSTOM 课表底本冻结。支持切课表、切学期周、另存为、改名、删表、设置、自建课增删改。
 */
class ScheduleViewModel : ViewModel() {

    data class ProfileMeta(
        val id: String,
        val name: String,
        val kind: ProfileKind,
        val termValue: String?,
    )

    data class UiState(
        val loading: Boolean = true,
        val scanning: Boolean = false,
        val error: String? = null,
        val account: String? = null,
        val profiles: List<ProfileMeta> = emptyList(),
        val activeProfileId: String? = null,
        val activeProfileName: String = "",
        val activeIsCustom: Boolean = false,
        val settings: ScheduleSettings = ScheduleSettings(),
        val currentWeek: Int? = null,
        val selectedWeek: Int = 1,
        val display: List<DisplayCourse> = emptyList(),
        val remarks: List<Remark> = emptyList(),
        val isLocalSource: Boolean = false, // 当前是本地「WebVPN 课表」源（数据靠 WebView 导入）
    )

    private val acc = Graph.accountManager
    private val store = Graph.scheduleStore
    private val jw = Graph.jwRepository

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var userPickedWeek = false
    private var lastReschedKey: String? = null // 当前渲染课表的指纹，变化即重排上课提醒

    init {
        viewModelScope.launch {
            acc.active.collect { account ->
                userPickedWeek = false
                if (account == null) {
                    _state.value = UiState(loading = false, error = "请先在「我的」里绑定教务账号")
                } else {
                    onAccountSelected(account)
                }
            }
        }
        viewModelScope.launch { store.root.collect { render() } }
        viewModelScope.launch { if (acc.activeAccount == null) acc.refresh() }
        // 教务短信验证通过后自动重试拉取（解决会话过期/扫描时触发 MFA）
        viewModelScope.launch { JwMfaState.passedTick.drop(1).collect { retry() } }
    }

    private suspend fun onAccountSelected(account: String) {
        if (AccountManager.isLocal(account)) {
            if (MemberState.isJw) {
                // 教务模式落到本地 Web 源（如重启残留）：自动切回上次的教务账号；
                // 无教务账号则置空 → 触发绑定提示，绝不在教务模式展示 Web 课表
                acc.activateJwAccount()
                return
            }
            render() // Web 模式本地源：不扫教务，直接渲染本地数据（无则提示导入）
            return
        }
        val data = store.accountData(account)
        if (!data.scanned || data.profiles.none { it.kind == ProfileKind.TERM }) {
            scanTerms(account)
        } else {
            render()
        }
    }

    // ---------- 用户操作 ----------

    fun selectProfile(id: String) {
        val account = acc.activeAccount ?: return
        userPickedWeek = false
        store.setActiveProfile(account, id)
    }

    fun selectWeek(week: Int) {
        userPickedWeek = true
        _state.value = _state.value.copy(
            selectedWeek = week.coerceIn(1, _state.value.settings.totalWeeks),
        )
    }

    fun gotoCurrentWeek() {
        val cw = _state.value.currentWeek ?: return
        userPickedWeek = true
        _state.value = _state.value.copy(selectedWeek = cw.coerceIn(1, _state.value.settings.totalWeeks))
    }

    /** 刷新：仅重拉当前 TERM 课表的教务底本；CUSTOM 课表为冻结，不刷新。 */
    fun refresh() {
        val account = acc.activeAccount ?: return
        if (AccountManager.isLocal(account)) return // 本地源靠 WebView 导入刷新，不走后端
        val p = activeProfile(account) ?: return
        if (p.kind != ProfileKind.TERM || p.termValue == null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            when (val r = jw.schedule(account, p.termValue)) {
                is Outcome.Success -> {
                    cacheTermData(account, p.termValue, r.data) // 触发 render
                    _state.value = _state.value.copy(loading = false)
                }
                is Outcome.Error -> _state.value = _state.value.copy(loading = false, error = r.message)
            }
        }
    }

    /** 重新扫描该学号的所有有数据学期。本地 WebVPN 源不扫描（靠导入）。 */
    fun rescan() {
        val account = acc.activeAccount ?: return
        if (AccountManager.isLocal(account)) return
        viewModelScope.launch { scanTerms(account) }
    }

    /** 另存为新课表：把当前展示（教务底本 + 自建课）冻结成独立 CUSTOM 课表，重拉不再覆盖。 */
    fun saveAsNew(name: String) {
        val account = acc.activeAccount ?: return
        val p = activeProfile(account) ?: return
        val frozen = baseCached(account, p)
        val profile = ScheduleProfile(
            id = "custom:" + UUID.randomUUID(),
            name = name.ifBlank { p.name + " 副本" },
            kind = ProfileKind.CUSTOM,
            termValue = p.termValue, // 记下来源学期，副本在三级菜单里挂到对应学年/学期下
            settings = p.settings,
            customCourses = p.customCourses,
            frozenCourses = frozen,
        )
        store.addProfile(account, profile, makeActive = true)
    }

    fun renameProfile(id: String, name: String) {
        val account = acc.activeAccount ?: return
        if (name.isNotBlank()) store.renameProfile(account, id, name)
    }

    fun deleteProfile(id: String) {
        val account = acc.activeAccount ?: return
        store.removeProfile(account, id)
    }

    fun saveSettings(settings: ScheduleSettings) {
        val account = acc.activeAccount ?: return
        val id = _state.value.activeProfileId ?: return
        userPickedWeek = false
        store.updateSettings(account, id, settings)
    }

    fun upsertCourse(course: CustomCourse) {
        val account = acc.activeAccount ?: return
        val id = _state.value.activeProfileId ?: return
        store.upsertCourse(account, id, course)
    }

    fun deleteCourse(courseId: String) {
        val account = acc.activeAccount ?: return
        val id = _state.value.activeProfileId ?: return
        store.deleteCourse(account, id, courseId)
    }

    fun retry() {
        val account = acc.activeAccount
        if (account == null) viewModelScope.launch { acc.refresh() }
        else viewModelScope.launch { scanTerms(account) }
    }

    // ---------- 扫描有数据学期 ----------

    /**
     * 扫描有数据学期：以**当前学期为锚**，向过去/将来用 [TermScan] 统一逻辑推进——遇连续 2 个空学期或达上限即止
     * （当前若为大四下这类空学期也照常尝试、不会因单个空学期提前结束），默认按日期选中当前/下一学期。
     * 与 WebView 抓取共用同一算法。
     */
    private suspend fun scanTerms(account: String) {
        _state.value = _state.value.copy(scanning = true, loading = true, error = null, account = account)
        val first = jw.schedule(account, null)
        if (first is Outcome.Error) {
            _state.value = _state.value.copy(scanning = false, loading = false, error = first.message)
            return
        }
        val data0 = (first as Outcome.Success).data
        val currentTerm = data0.term
        val labels = data0.terms.associate { it.value to it.label }
        fun profileOf(term: String) = ScheduleProfile(
            id = "term:$term",
            name = labels[term]?.takeIf { it.isNotBlank() } ?: TermUtil.label(term),
            kind = ProfileKind.TERM,
            termValue = term,
        )

        if (currentTerm == null || !TermUtil.isTerm(currentTerm)) {
            // 识别不出当前学期：退化为仅首个/当前学期
            if (currentTerm != null) cacheTermData(account, currentTerm, data0)
            val fb = currentTerm?.let { profileOf(it) } ?: data0.terms.firstOrNull()?.let { termProfile(it) }
            store.replaceTermProfiles(account, listOfNotNull(fb))
            applyAutoStarts(account)
            _state.value = _state.value.copy(scanning = false, loading = false)
            render()
            return
        }
        cacheTermData(account, currentTerm, data0)

        // 以当前学期为锚扫描；hasData 抓取并缓存某学期、返回是否有课
        val withData = TermScan.scanAround(currentTerm) { term ->
            val courses: List<CourseDto> = if (term == currentTerm) data0.courses else {
                when (val r = jw.schedule(account, term)) {
                    // 仅当返回的就是所请求学期且非空才算有课；强智把无效学期回显当前学期的情况按“无课”处理
                    is Outcome.Success ->
                        if (r.data.term?.trim() == term && r.data.courses.isNotEmpty()) {
                            cacheTermData(account, term, r.data); r.data.courses
                        } else emptyList()
                    is Outcome.Error -> emptyList()
                }
            }
            courses.isNotEmpty()
        }

        // 当前学期即使空也展示（如大四下），保持学期完整；其余仅列有课学期
        val terms = (withData + currentTerm).distinct().sortedDescending()
        store.replaceTermProfiles(account, terms.map { profileOf(it) }) // 持久化 + scanned=true → 触发 render
        // 默认选中：按日期选当前/下一学期；当前无课则选最近有课学期
        val month = java.time.LocalDate.now().monthValue
        val def = TermScan.defaultTerm(currentTerm, withData, month) ?: terms.firstOrNull()
        def?.let { store.setActiveProfile(account, "term:$it") }
        applyAutoStarts(account) // profiles 创建后补刷自动开学日期
        _state.value = _state.value.copy(scanning = false, loading = false)
        render()
    }

    // ---------- 渲染 ----------

    private fun render() {
        val account = acc.activeAccount ?: return
        val data = store.accountData(account)
        val profiles = data.profiles
        val activeId = data.activeProfileId?.takeIf { id -> profiles.any { it.id == id } }
            ?: profiles.firstOrNull()?.id
        val active = profiles.firstOrNull { it.id == activeId }
        val settings = active?.settings ?: ScheduleSettings()
        val cw = WeekUtil.currentWeek(settings.semesterStartDate)
        val sel = if (!userPickedWeek) (cw ?: _state.value.selectedWeek).coerceIn(1, settings.totalWeeks)
        else _state.value.selectedWeek.coerceIn(1, settings.totalWeeks)
        val remarks = if (active?.kind == ProfileKind.TERM)
            (data.termRemarks ?: emptyMap())[active.termValue] ?: emptyList()
        else emptyList()

        val display = buildDisplay(data, active)
        _state.value = _state.value.copy(
            loading = false,
            account = account,
            profiles = profiles.map { ProfileMeta(it.id, it.name, it.kind, it.termValue) },
            activeProfileId = activeId,
            activeProfileName = active?.name ?: "",
            activeIsCustom = active?.kind == ProfileKind.CUSTOM,
            settings = settings,
            currentWeek = cw,
            selectedWeek = sel,
            display = display,
            remarks = remarks,
            isLocalSource = AccountManager.isLocal(account),
            error = if (profiles.isEmpty() && !_state.value.scanning) _state.value.error else null,
        )
        // 让上课提醒始终以「当前渲染的课表」为准：账号/课表/模式/开学日期 + 课程内容（增删改任一字段）变化即重排。
        // 用排程相关字段（星期/节次/名称/地点/周次）的内容指纹，而非课程数——否则「改时间但数量不变」会漏排。
        val coursesSig = display.joinToString(";") {
            "${it.day}/${it.startNode}-${it.endNode}/${it.name}/${it.location}/${it.weeks.joinToString(",")}"
        }.hashCode()
        val key = "$account|$activeId|${settings.semesterStartDate}|$coursesSig"
        if (key != lastReschedKey) {
            lastReschedKey = key
            ReminderScheduler.reschedule(Graph.appContext)
        }
    }

    private fun buildDisplay(data: AccountData, p: ScheduleProfile?): List<DisplayCourse> {
        if (p == null) return emptyList()
        val base = when (p.kind) {
            ProfileKind.TERM -> data.termCourses[p.termValue] ?: emptyList()
            ProfileKind.CUSTOM -> p.frozenCourses
        }
        val out = ArrayList<DisplayCourse>(base.size + p.customCourses.size)
        for (c in base) {
            val nodes = Periods.nodesOfSection(c.sectionIndex)
            out.add(
                DisplayCourse(
                    name = c.name,
                    teacher = c.teacher,
                    location = c.location,
                    day = c.day,
                    startNode = nodes.first,
                    endNode = nodes.last,
                    weeks = WeekUtil.parseWeeks(c.weeksRaw),
                    colorIndex = if (c.colorIndex > 0) c.colorIndex else colorIndexOf(c.name),
                    isCustom = false,
                    weeksLabel = c.weeksRaw,
                ),
            )
        }
        for (c in p.customCourses) {
            out.add(
                DisplayCourse(
                    name = c.name,
                    teacher = c.teacher,
                    location = c.location,
                    day = c.day,
                    startNode = c.startNode,
                    endNode = c.endNode,
                    weeks = c.weeks,
                    colorIndex = c.colorIndex,
                    isCustom = true,
                    customId = c.id,
                    weeksLabel = WeekUtil.formatWeeks(c.weeks),
                    customColor = c.customColor,
                ),
            )
        }
        return out
    }

    // ---------- 工具 ----------

    private fun activeProfile(account: String): ScheduleProfile? {
        val data = store.accountData(account)
        val id = data.activeProfileId ?: data.profiles.firstOrNull()?.id
        return data.profiles.firstOrNull { it.id == id }
    }

    private fun baseCached(account: String, p: ScheduleProfile): List<CachedCourse> = when (p.kind) {
        ProfileKind.TERM -> store.accountData(account).termCourses[p.termValue] ?: emptyList()
        ProfileKind.CUSTOM -> p.frozenCourses
    }

    /** 本次会话内捕获到的「学期→开学日期(教务教学周历)」，用于 profile 创建后补刷。 */
    private val termStarts = HashMap<String, String>()

    private fun cacheTermData(account: String, term: String?, data: ScheduleData) {
        if (term == null) return
        store.setTermCourses(account, term, data.courses.map { it.toCached() })
        store.setTermRemarks(account, term, data.remarks.map { it.toRemark() })
        // 教务教学周历给出的开学日期：记下并自动写入对应课表（不覆盖用户手动设置）
        data.semesterStartDate?.takeIf { it.isNotBlank() }?.let { start ->
            termStarts[term] = start
            applyAutoStart(account, term, start)
        }
    }

    /** 把自动开学日期写到对应 TERM 课表；不覆盖用户手动设过的；profile 不存在则跳过（稍后由 applyAutoStarts 补）。 */
    private fun applyAutoStart(account: String, term: String, start: String) {
        val p = store.accountData(account).profiles
            .firstOrNull { it.kind == ProfileKind.TERM && it.termValue == term } ?: return
        if (!p.settings.semesterStartDateManual && p.settings.semesterStartDate != start) {
            store.updateSettings(account, p.id,
                p.settings.copy(semesterStartDate = start, semesterStartDateManual = false))
        }
    }

    /** profiles 创建/替换后，补刷本会话已捕获的自动开学日期。 */
    private fun applyAutoStarts(account: String) {
        store.accountData(account).profiles
            .filter { it.kind == ProfileKind.TERM && it.termValue != null }
            .forEach { p -> termStarts[p.termValue]?.let { applyAutoStart(account, p.termValue!!, it) } }
    }

    private fun RemarkDto.toRemark() = Remark(
        name = name ?: "",
        teacher = teacher ?: "",
        weeks = weeks ?: "",
        clazz = clazz ?: "",
    )

    private fun CourseDto.toCached() = CachedCourse(
        name = name ?: "",
        teacher = teacher ?: "",
        location = room ?: "",
        day = day,
        sectionIndex = sectionIndex.coerceAtLeast(1),
        weeksRaw = weeks,
        colorIndex = colorIndexOf(name),
    )

    private fun termProfile(t: TermDto) = ScheduleProfile(
        id = "term:" + t.value,
        name = t.label.ifBlank { t.value },
        kind = ProfileKind.TERM,
        termValue = t.value,
    )
}
