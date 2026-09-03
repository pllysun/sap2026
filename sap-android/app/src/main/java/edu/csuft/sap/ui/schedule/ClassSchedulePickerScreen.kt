package edu.csuft.sap.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import edu.csuft.sap.data.account.AccountManager
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.dto.ClassOptionDto
import edu.csuft.sap.data.remote.dto.ClassScheduleTermDto
import edu.csuft.sap.data.schedule.CachedCourse
import edu.csuft.sap.di.Graph
import edu.csuft.sap.ui.common.ErrorRetry
import edu.csuft.sap.ui.common.LoadingBox
import edu.csuft.sap.ui.common.OptionSheet
import edu.csuft.sap.ui.common.SapCard
import edu.csuft.sap.ui.icons.AppIcons
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.Base64

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
    var terms by remember { mutableStateOf<List<ClassScheduleTermDto>>(emptyList()) }
    var options by remember { mutableStateOf<List<ClassOptionDto>>(emptyList()) }
    var selectedTerm by remember { mutableStateOf("") }
    var college by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("") }
    var major by remember { mutableStateOf("") }
    var className by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showTerms by remember { mutableStateOf(false) }
    var showColleges by remember { mutableStateOf(false) }
    var showGrades by remember { mutableStateOf(false) }
    var showMajors by remember { mutableStateOf(false) }
    var showClasses by remember { mutableStateOf(false) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    val root by Graph.scheduleStore.root.collectAsState()
    val cached = remember(root) { Graph.scheduleStore.cachedClassAccounts() }

    LaunchedEffect(reloadNonce) {
        loading = true
        error = null
        when (val result = Graph.classScheduleRepository.terms()) {
            is Outcome.Success -> {
                terms = result.data
                selectedTerm = result.data.firstOrNull()?.value.orEmpty()
                loading = false
            }
            is Outcome.Error -> {
                error = result.message
                loading = false
            }
        }
    }

    LaunchedEffect(selectedTerm) {
        if (selectedTerm.isBlank()) return@LaunchedEffect
        loading = true
        error = null
        college = ""; grade = ""; major = ""; className = ""
        when (val result = Graph.classScheduleRepository.classes(selectedTerm, null, null, null)) {
            is Outcome.Success -> { options = result.data; loading = false }
            is Outcome.Error -> { error = result.message; options = emptyList(); loading = false }
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
            IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "返回") }
            Column(Modifier.weight(1f)) {
                Text("班级课表", fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                Text("选择一个班级，查看该班级的公共课表", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (cached.isNotEmpty()) {
                Text("已缓存 ${cached.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
            }
        }
        if (loading && terms.isEmpty() && cached.isEmpty()) {
            LoadingBox()
        } else {
            // 选择器在小屏设备上可能同时包含五级筛选和缓存列表，整个页面可自然滚动，
            // 避免底部“使用此班级课表”按钮被系统导航栏或弹层遮住。
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                if (terms.isNotEmpty()) {
                    Selector("学期", terms.firstOrNull { it.value == selectedTerm }?.label ?: selectedTerm, terms.map { it.value to (it.label.ifBlank { it.value }) }, selectedTerm, showTerms, { showTerms = it }) { selectedTerm = it }
                    Selector("学院", college.ifBlank { "请选择学院" }, colleges.map { it to it }, college, showColleges, { showColleges = it }) { college = it; major = ""; className = "" }
                    if (grades.isNotEmpty()) {
                        Selector("年级", grade.ifBlank { "请选择年级" }, grades.map { it to it }, grade, showGrades, { showGrades = it }) { grade = it; major = ""; className = "" }
                    }
                    Selector("专业", major.ifBlank { "请选择专业" }, majors.map { it to it }, major, showMajors, { showMajors = it }) { major = it; className = "" }
                    Selector("班级", className.ifBlank { "请选择班级" }, classes.map { it to it }, className, showClasses, { showClasses = it }) { className = it }
                    Spacer(Modifier.height(8.dp))
                    val selectedSummary = listOfNotNull(
                        college.takeIf(String::isNotBlank),
                        grade.takeIf(String::isNotBlank),
                        major.takeIf(String::isNotBlank),
                        className.takeIf(String::isNotBlank),
                    ).joinToString(" · ")
                    if (selectedSummary.isNotBlank()) {
                        Text(selectedSummary, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Button(
                        enabled = selectedTerm.isNotBlank() && college.isNotBlank() && major.isNotBlank() && className.isNotBlank() && !importing,
                        onClick = {
                            importing = true; error = null
                            scope.launch {
                                when (val result = Graph.classScheduleRepository.schedule(selectedTerm, college, major, className, grade.ifBlank { null })) {
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
                                        )
                                        Graph.accountManager.useClass(key)
                                        importing = false
                                        onSelected()
                                    }
                                    is Outcome.Error -> { importing = false; error = result.message }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { if (importing) CircularProgressIndicator(strokeWidth = 2.dp) else Text("使用此班级课表") }
                }
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                if (error != null && terms.isEmpty() && cached.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    ErrorRetry(error!!, onRetry = { reloadNonce++ })
                }
                if (cached.isNotEmpty()) {
                    Text("最近使用", fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
                    Text("已下载的班级课表可在离线时直接切换", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        cached.forEach { (account, name) ->
                            SapCard(onClick = { Graph.accountManager.setActive(account); onSelected() }) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(name, fontSize = 14.sp, lineHeight = 20.sp)
                                        Text("已缓存 · 点击切换", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp))
                                    }
                                    Text("›", color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }
        }
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
    onPick: (String) -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        OutlinedButton(onClick = { onExpanded(true) }, enabled = options.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("$label：$value", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("⌄")
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
    val raw = listOf(college, grade, major, className).joinToString("\u001F")
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).take(22)
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
