package edu.csuft.sap.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.dto.CourseDto
import edu.csuft.sap.data.remote.dto.RemarkDto
import edu.csuft.sap.data.remote.dto.ScheduleData
import edu.csuft.sap.data.remote.dto.TermDto
import edu.csuft.sap.data.account.AccountManager
import edu.csuft.sap.data.schedule.AccountData
import edu.csuft.sap.data.schedule.CachedCourse
import edu.csuft.sap.data.schedule.CustomCourse
import edu.csuft.sap.data.schedule.DisplayCourse
import edu.csuft.sap.data.schedule.Periods
import edu.csuft.sap.data.schedule.Remark
import edu.csuft.sap.data.schedule.ProfileKind
import edu.csuft.sap.data.schedule.ScheduleProfile
import edu.csuft.sap.data.schedule.ScheduleSettings
import edu.csuft.sap.data.schedule.WeekUtil
import edu.csuft.sap.di.Graph
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
    }

    private suspend fun onAccountSelected(account: String) {
        if (AccountManager.isLocal(account)) {
            render() // 本地 WebVPN 源：不扫教务，直接渲染本地数据（无则提示导入）
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

    private suspend fun scanTerms(account: String) {
        _state.value = _state.value.copy(scanning = true, loading = true, error = null, account = account)
        val first = jw.schedule(account, null)
        if (first is Outcome.Error) {
            _state.value = _state.value.copy(scanning = false, loading = false, error = first.message)
            return
        }
        val data0 = (first as Outcome.Success).data
        val currentTerm = data0.term
        val terms = data0.terms.sortedByDescending { it.value }
        if (currentTerm != null) cacheTermData(account, currentTerm, data0)

        val startIdx = terms.indexOfFirst { it.value == currentTerm }.let { if (it < 0) 0 else it }
        val scanList = terms.drop(startIdx)

        val profiles = ArrayList<ScheduleProfile>()
        var foundData = false
        var fetches = 0
        for (t in scanList) {
            val courses: List<CourseDto> = when {
                t.value == currentTerm -> data0.courses
                fetches >= MAX_FETCH -> break
                else -> {
                    fetches++
                    when (val r = jw.schedule(account, t.value)) {
                        is Outcome.Success -> {
                            cacheTermData(account, t.value, r.data)
                            r.data.courses
                        }
                        is Outcome.Error -> emptyList()
                    }
                }
            }
            if (courses.isNotEmpty()) {
                foundData = true
                profiles.add(termProfile(t))
            } else if (foundData) {
                break // 入学前空学期 → 停，更早的不再加载
            }
        }
        if (profiles.isEmpty()) {
            terms.firstOrNull()?.let { profiles.add(termProfile(it)) }
        }
        store.replaceTermProfiles(account, profiles) // 持久化 + scanned=true → 触发 render
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
            display = buildDisplay(data, active),
            remarks = remarks,
            isLocalSource = AccountManager.isLocal(account),
            error = if (profiles.isEmpty() && !_state.value.scanning) _state.value.error else null,
        )
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

    private companion object {
        const val MAX_FETCH = 14
    }
}
