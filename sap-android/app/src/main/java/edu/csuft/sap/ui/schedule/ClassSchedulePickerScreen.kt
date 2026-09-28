package edu.csuft.sap.ui.schedule

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.csuft.sap.data.account.AccountManager
import edu.csuft.sap.data.account.CurrentAccount
import edu.csuft.sap.data.account.ConnectivityState
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.dto.ClassOptionDto
import edu.csuft.sap.data.remote.dto.ClassScheduleTermDto
import edu.csuft.sap.data.schedule.CachedCourse
import edu.csuft.sap.di.Graph
import edu.csuft.sap.ui.common.OptionSheet
import edu.csuft.sap.ui.icons.AppIcons
import edu.csuft.sap.ui.icons.ChevronIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class CatalogRequest(val term: String? = null, val revision: Int = 0, val force: Boolean = false)

/**
 * 班级课表选择器。网络请求只在学期变化和用户点击“加载课表”时串行发起；
 * 已下载的选择会落进独立缓存槽，离线时仍可切换历史班级。
 */
@Composable
fun ClassSchedulePickerScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSelected: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val repository = Graph.classScheduleRepository
    val online = ConnectivityState.online
    var terms by remember { mutableStateOf(emptyList<ClassScheduleTermDto>()) }
    var selectedTerm by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(emptyList<ClassOptionDto>()) }
    var college by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("") }
    var major by remember { mutableStateOf("") }
    var className by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var importing by remember { mutableStateOf(false) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showTerms by remember { mutableStateOf(false) }
    var showColleges by remember { mutableStateOf(false) }
    var showGrades by remember { mutableStateOf(false) }
    var showMajors by remember { mutableStateOf(false) }
    var showClasses by remember { mutableStateOf(false) }
    var request by remember { mutableStateOf(CatalogRequest()) }
    var menuOpen by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selectedAccounts by remember { mutableStateOf(emptySet<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val root by Graph.scheduleStore.root.collectAsState()
    val activeAccount by Graph.accountManager.active.collectAsState()
    val accountContext by Graph.accountManager.contextVersion.collectAsState()
    val cached = remember(root, accountContext) { Graph.scheduleStore.cachedClassAccounts() }

    fun refreshCatalog() {
        loading = true
        request = CatalogRequest(selectedTerm.ifBlank { null }, request.revision + 1, force = true)
    }
    fun toggleAccount(account: String) {
        selectedAccounts = if (account in selectedAccounts) selectedAccounts - account else selectedAccounts + account
    }
    BackHandler(enabled = selecting) { selecting = false; selectedAccounts = emptySet() }
    LaunchedEffect(accountContext) {
        selecting = false
        selectedAccounts = emptySet()
        confirmDelete = false
    }

    // 一次加载由同一个协程负责，避免学期和班级两个请求互相覆盖 loading/error。
    // 目录 JSON 的读取/解析也放在 IO，首帧只呈现明确的加载状态。
    LaunchedEffect(request, online) {
        if (!online) {
            downloadJob?.cancel()
            downloadJob = null
            importing = false
            loading = false
            error = null
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            when (val result = withContext(Dispatchers.IO) { repository.terms(force = request.force) }) {
                is Outcome.Success -> terms = result.data
                is Outcome.Error -> {
                    error = result.message
                    return@LaunchedEffect
                }
            }
            val term = (request.term ?: selectedTerm).takeIf { value -> terms.any { it.value == value } }
                ?: terms.firstOrNull()?.value.orEmpty()
            if (term != selectedTerm) {
                college = ""; grade = ""; major = ""; className = ""
                options = emptyList()
            }
            selectedTerm = term
            if (term.isBlank()) return@LaunchedEffect
            when (val result = withContext(Dispatchers.IO) { repository.classes(term, null, null, null, force = request.force) }) {
                is Outcome.Success -> {
                    options = result.data
                    if (options.none { it.college == college }) college = ""
                    if (options.none { it.college == college && it.grade.orEmpty() == grade }) grade = ""
                    if (options.none { it.college == college && it.grade.orEmpty() == grade && it.major == major }) major = ""
                    if (options.none { it.college == college && it.grade.orEmpty() == grade && it.major == major && it.className == className }) className = ""
                }
                is Outcome.Error -> error = result.message
            }
        } finally {
            loading = false
        }
    }

    val colleges = remember(options) { options.mapNotNull { it.college?.takeIf(String::isNotBlank) }.distinct().sorted() }
    val grades = remember(options, college) {
        options.filter { college.isBlank() || it.college == college }
            .mapNotNull { it.grade?.takeIf(String::isNotBlank) }.distinct().sorted()
    }
    val majors = remember(options, college, grade) {
        options.filter { (college.isBlank() || it.college == college) && (grade.isBlank() || it.grade == grade) }
            .mapNotNull { it.major?.takeIf(String::isNotBlank) }.distinct().sorted()
    }
    val classes = remember(options, college, grade, major) {
        options.filter { (college.isBlank() || it.college == college) &&
            (grade.isBlank() || it.grade == grade) && (major.isBlank() || it.major == major) }
            .mapNotNull { it.className?.takeIf(String::isNotBlank) }.distinct().sorted()
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (selecting) { selecting = false; selectedAccounts = emptySet() } else onBack()
            }) { Icon(AppIcons.Back, contentDescription = if (selecting) "取消选择" else "返回") }
            Text(if (selecting) "已选择 ${selectedAccounts.size} 项" else "班级课表", fontSize = 20.sp,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (selecting) {
                val allSelected = selectedAccounts.size == cached.size
                TextButton(onClick = { selectedAccounts = if (allSelected) emptySet() else cached.map { it.first }.toSet() }) {
                    Text(if (allSelected) "取消全选" else "全选")
                }
            } else {
                Box {
                    IconButton(onClick = { menuOpen = true }, enabled = !importing) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "班级课表菜单")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("刷新目录") },
                            leadingIcon = { Icon(AppIcons.Refresh, null) }, enabled = online && !loading,
                            onClick = { menuOpen = false; refreshCatalog() })
                        DropdownMenuItem(text = { Text("批量删除") },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) }, enabled = cached.isNotEmpty(),
                            onClick = { menuOpen = false; selectedAccounts = emptySet(); selecting = true })
                    }
                }
            }
        }
        run {
            // 选择器在小屏设备上可能同时包含五级筛选和缓存列表，整个页面可自然滚动，
            // 避免底部“使用此班级课表”按钮被系统导航栏或弹层遮住。
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                if (!online && !selecting) {
                    Surface(Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(18.dp)) {
                            Text("离线班级课表", style = MaterialTheme.typography.titleMedium)
                            Text(if (cached.isEmpty()) "本机还没有下载班级课表，请联网后选择并下载。"
                                else "可直接切换已下载课表，下载其他班级需联网。",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
                if (!selecting && online) {
                    if (loading) {
                        Surface(Modifier.fillMaxWidth().padding(top = 16.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.fillMaxWidth().heightIn(min = 320.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center) {
                                CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp)
                                Text("正在加载班级目录…", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 18.dp))
                            }
                        }
                    } else {
                        Text("查找班级", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))
                        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                            Column {
                                Selector("学期", terms.firstOrNull { it.value == selectedTerm }?.label ?: selectedTerm.ifBlank { "暂无学期" }, terms.map { it.value to (it.label.ifBlank { it.value }) }, selectedTerm, showTerms, { showTerms = it }, !importing) {
                                    if (it != selectedTerm) { loading = true; request = CatalogRequest(it, request.revision) }
                                }
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Selector("学院", college.ifBlank { "请选择学院" }, colleges.map { it to it }, college, showColleges, { showColleges = it }, !importing) { college = it; grade = ""; major = ""; className = "" }
                                Selector("年级", grade.ifBlank { if (college.isNotBlank() && grades.isEmpty()) "未提供年级" else "请选择年级" }, if (college.isBlank()) emptyList() else grades.map { it to it }, grade, showGrades, { showGrades = it }, !importing) { grade = it; major = ""; className = "" }
                                Selector("专业", major.ifBlank { "请选择专业" }, if (college.isBlank() || (grades.isNotEmpty() && grade.isBlank())) emptyList() else majors.map { it to it }, major, showMajors, { showMajors = it }, !importing) { major = it; className = "" }
                                Selector("班级", className.ifBlank { "请选择班级" }, if (major.isBlank()) emptyList() else classes.map { it to it }, className, showClasses, { showClasses = it }, !importing) { className = it }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            enabled = selectedTerm.isNotBlank() && college.isNotBlank() && major.isNotBlank() && className.isNotBlank() && !importing && !loading,
                            onClick = {
                                importing = true; error = null
                                val importOwner = CurrentAccount.key
                                downloadJob = scope.launch {
                                    val result = repository.schedule(selectedTerm, college, major, className, grade.ifBlank { null })
                                    ensureActive()
                                    if (importOwner != CurrentAccount.key) {
                                        importing = false
                                        return@launch
                                    }
                                    when (result) {
                                        is Outcome.Success -> {
                                            val key = stableKey(college, grade, major, className)
                                            val displayName = listOfNotNull(
                                                college.takeIf(String::isNotBlank),
                                                grade.takeIf(String::isNotBlank),
                                                major.takeIf(String::isNotBlank),
                                                className.takeIf(String::isNotBlank),
                                            ).joinToString(" · ")
                                            Graph.scheduleStore.importClass(
                                                AccountManager.CLASS_ACCOUNT_PREFIX + key,
                                                selectedTerm,
                                                className,
                                                result.data.courses.map { it.toCached() },
                                                result.data.semesterStartDate,
                                                displayName = displayName,
                                                identity = edu.csuft.sap.data.schedule.ClassIdentity(college, grade, major, className),
                                            )
                                            Graph.accountManager.useClass(key)
                                            Graph.scheduleStore.finishScheduleSync(AccountManager.CLASS_ACCOUNT_PREFIX + key, setOf(selectedTerm))
                                            importing = false
                                            onSelected()
                                        }
                                        is Outcome.Error -> { importing = false; error = result.message }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            if (importing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(if (importing) "正在加载课表…" else "使用此班级课表", modifier = Modifier.padding(horizontal = 8.dp))
                        }
                    }
                }
                if (!selecting && online && error != null) {
                    Surface(Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Column(Modifier.padding(16.dp)) {
                            Text(error!!,
                                color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { refreshCatalog() }, enabled = !loading && !importing) { Text("重新加载") }
                        }
                    }
                }
                if (cached.isNotEmpty()) {
                    Text(if (selecting) "选择要删除的课表" else if (!online) "已下载课表" else "最近使用", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
                    Column(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        cached.forEach { (account, name) ->
                            val parts = name.split(" · ")
                            val isActive = account == activeAccount
                            val highlighted = if (selecting) account in selectedAccounts else isActive
                            Surface(
                                modifier = Modifier.fillMaxWidth().then(if (selecting) Modifier.toggleable(
                                    value = account in selectedAccounts, role = Role.Checkbox, onValueChange = { toggleAccount(account) },
                                ) else Modifier.clickable(enabled = !importing) { Graph.accountManager.setActive(account); onSelected() }),
                                shape = RoundedCornerShape(20.dp),
                                color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(parts.last(), fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold,
                                            color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                        if (parts.size > 1) Text(parts.dropLast(1).joinToString(" · "), fontSize = 12.sp, lineHeight = 18.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
                                    }
                                    if (selecting) Checkbox(checked = account in selectedAccounts, onCheckedChange = null, modifier = Modifier.padding(start = 12.dp))
                                    else if (isActive) Icon(AppIcons.Check, "已选中", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp).size(20.dp))
                                    else ChevronIcon(Modifier.padding(start = 12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (selecting) {
            Button(onClick = { confirmDelete = true }, enabled = selectedAccounts.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(16.dp)) {
                Text("删除所选（${selectedAccounts.size}）")
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(onDismissRequest = { confirmDelete = false },
            title = { Text("删除 ${selectedAccounts.size} 个课表？") },
            text = { Text("将从本机删除所选班级的全部学期和自建课程。" +
                if (activeAccount in selectedAccounts) "当前课表也会删除，随后切换到其他课表；没有剩余课表时需重新选择班级。" else "") },
            confirmButton = {
                TextButton(onClick = {
                    Graph.classScheduleCache.delete(selectedAccounts)
                    confirmDelete = false
                    selectedAccounts = emptySet()
                    selecting = false
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun Selector(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    selectedKey: String?,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    inputEnabled: Boolean = true,
    onPick: (String) -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        val enabled = inputEnabled && options.isNotEmpty()
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(enabled = enabled) { onExpanded(true) }.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 20.dp))
            Text(value, modifier = Modifier.weight(1f), fontSize = 15.sp, lineHeight = 22.sp,
                color = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    else if (selectedKey.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            Icon(AppIcons.DropDown, null, modifier = Modifier.padding(start = 12.dp).size(18.dp), tint = MaterialTheme.colorScheme.outline)
        }
        if (expanded) {
            // 统一使用底部选项面板，适配长学院/专业/班级列表，并避免在 DropdownMenu
            // 中嵌套可组合滚动容器导致 SubcomposeLayout intrinsic measurement 崩溃。
            OptionSheet(
                title = "选择$label",
                options = options,
                selected = options.firstOrNull { it.first == selectedKey },
                label = { it.second },
                onPick = { option -> onPick(option.first); onExpanded(false) },
                onDismiss = { onExpanded(false) },
            )
        }
    }
}

private fun stableKey(college: String, grade: String, major: String, className: String): String {
    // 缓存按班级身份归槽，而不是按“班级 + 学期”拆槽。同一班级导入多个学期后，
    // 仍然只有一个离线缓存，可在课表设置里切换其中的学期。
    return edu.csuft.sap.data.schedule.ClassIdentity(college, grade, major, className).key()
}

private fun edu.csuft.sap.data.remote.dto.CourseDto.toCached() = CachedCourse(
    name = name ?: "",
    teacher = teacher ?: "",
    location = room ?: "",
    day = day,
    sectionIndex = sectionIndex.coerceAtLeast(1),
    weeksRaw = weeks,
    colorIndex = edu.csuft.sap.ui.theme.colorIndexOf(name),
)
