package edu.csuft.sap.ui.feedback

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import edu.csuft.sap.data.remote.dto.FeedbackCommentDto
import edu.csuft.sap.data.remote.dto.FeedbackIssueDto
import edu.csuft.sap.data.repository.FeedbackImageUpload
import edu.csuft.sap.ui.common.LoadingBox
import edu.csuft.sap.ui.common.SapCard
import edu.csuft.sap.ui.icons.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private enum class FeedbackRoute { LIST, CREATE, DETAIL }

private const val MAX_FEEDBACK_IMAGES = 4
private const val MAX_FEEDBACK_IMAGE_BYTES = 8 * 1024 * 1024

private data class SelectedFeedbackImage(
    val uri: Uri,
    val upload: FeedbackImageUpload,
)

private val categories = listOf(
    "BUG" to "问题反馈",
    "FEATURE" to "功能建议",
    "EXPERIENCE" to "体验优化",
    "OTHER" to "其他",
)

/** App 内嵌的软协课表 Issue 中心。 */
@Composable
fun FeedbackScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    vm: FeedbackViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    var route by remember { mutableStateOf(FeedbackRoute.LIST) }
    val context = LocalContext.current

    BackHandler {
        when (route) {
            FeedbackRoute.LIST -> onBack()
            FeedbackRoute.CREATE -> route = FeedbackRoute.LIST
            FeedbackRoute.DETAIL -> { vm.clearDetail(); route = FeedbackRoute.LIST }
        }
    }

    LaunchedEffect(state.notice) {
        state.notice?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            vm.consumeNotice()
        }
    }

    state.quotaMessage?.let { message ->
        AlertDialog(
            onDismissRequest = vm::consumeQuotaMessage,
            title = { Text("暂时无法提交") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = vm::consumeQuotaMessage) { Text("知道了") }
            },
        )
    }

    when (route) {
        FeedbackRoute.LIST -> FeedbackList(
            modifier = modifier,
            state = state,
            onBack = onBack,
            onCreate = { vm.clearError(); route = FeedbackRoute.CREATE },
            onOpen = { id -> route = FeedbackRoute.DETAIL; vm.open(id) },
            onRefresh = vm::refresh,
            onStatus = vm::setStatus,
            onCategory = vm::setCategory,
            onMine = vm::setMine,
            onSearch = vm::search,
        )
        FeedbackRoute.CREATE -> FeedbackCreate(
            modifier = modifier,
            state = state,
            onBack = { route = FeedbackRoute.LIST },
            onSubmit = { title, content, category, images ->
                vm.create(title, content, category, images) { route = FeedbackRoute.DETAIL }
            },
        )
        FeedbackRoute.DETAIL -> FeedbackDetail(
            modifier = modifier,
            state = state,
            onBack = { vm.clearDetail(); route = FeedbackRoute.LIST },
            onComment = vm::comment,
            onClose = vm::closeIssue,
            onRetry = { state.detailId?.let(vm::open) },
        )
    }
}

@Composable
private fun FeedbackList(
    modifier: Modifier,
    state: FeedbackViewModel.UiState,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit,
    onRefresh: () -> Unit,
    onStatus: (String) -> Unit,
    onCategory: (String) -> Unit,
    onMine: (Boolean) -> Unit,
    onSearch: (String) -> Unit,
) {
    var keyword by remember(state.keyword) { mutableStateOf(state.keyword) }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FeedbackTopBar("意见反馈", onBack) {
            TextButton(onClick = onCreate) { Text("新建") }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SapCard {
                    Text("一起把软协课表做得更好", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "提交问题或建议，查看大家的反馈和维护者处理进度。相同问题可以直接在已有 Issue 下补充。",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                        Text("提交新反馈")
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("搜索反馈") },
                    placeholder = { Text("输入功能或问题关键词") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(keyword) }),
                    trailingIcon = { TextButton(onClick = { onSearch(keyword) }) { Text("搜索") } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("OPEN" to "开放", "CLOSED" to "已关闭", "ALL" to "全部")) { item ->
                        FilterChip(selected = state.status == item.first, onClick = { onStatus(item.first) },
                            label = { Text(item.second) })
                    }
                    item {
                        FilterChip(selected = state.mine, onClick = { onMine(!state.mine) }, label = { Text("我的") })
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(selected = state.category == "ALL", onClick = { onCategory("ALL") }, label = { Text("全部分类") })
                    }
                    items(categories) { item ->
                        FilterChip(selected = state.category == item.first, onClick = { onCategory(item.first) },
                            label = { Text(item.second) })
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${state.total} 条反馈", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onRefresh, enabled = !state.loading) { Text("刷新") }
                }
            }
            if (state.loading && state.issues.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                    }
                }
            } else if (state.error != null && state.issues.isEmpty()) {
                item {
                    EmptyOrError(state.error, "重新加载", onRefresh)
                }
            } else if (state.issues.isEmpty()) {
                item { EmptyOrError("没有符合条件的反馈", null, null) }
            } else {
                items(state.issues, key = { it.id }) { issue ->
                    IssueCard(issue, onClick = { onOpen(issue.id) })
                }
            }
        }
    }
}

@Composable
private fun IssueCard(issue: FeedbackIssueDto, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick)
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(issue.status)
            CategoryPill(issue.categoryText, issue.category, Modifier.padding(start = 7.dp))
            Spacer(Modifier.weight(1f))
            Text("#${issue.id}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        }
        Text(
            issue.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            issue.content.replace("\n", " "),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${issue.reporterName ?: "用户"} · ${shortDate(issue.updatedAt)}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f),
            )
            if (issue.mine) Text("我的", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 10.dp))
            if (issue.images.isNotEmpty()) {
                Text("图片 ${issue.images.size}", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 10.dp))
            }
            Text("回复 ${issue.commentCount}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FeedbackCreate(
    modifier: Modifier,
    state: FeedbackViewModel.UiState,
    onBack: () -> Unit,
    onSubmit: (String, String, String, List<FeedbackImageUpload>) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("BUG") }
    var selectedImages by remember { mutableStateOf<List<SelectedFeedbackImage>>(emptyList()) }
    var imageError by remember { mutableStateOf<String?>(null) }
    var readingImages by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 反馈附件属于一次性选图：使用系统 Photo Picker 按所选 URI 授权，不申请整座相册的读取权限。
    // launcher 只会在用户点击“上传图片”时启动，进入反馈页本身不会触发任何授权 UI。
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_FEEDBACK_IMAGES),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            readingImages = true
            imageError = null
            val merged = (selectedImages.map { it.uri } + uris).distinct().take(MAX_FEEDBACK_IMAGES)
            runCatching { prepareFeedbackImages(context, merged) }
                .onSuccess { selectedImages = it }
                .onFailure { imageError = it.message ?: "图片读取失败，请重新选择" }
            readingImages = false
        }
    }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FeedbackTopBar("提交反馈", onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("选择分类", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 7.dp)) {
                    items(categories) { item ->
                        FilterChip(selected = category == item.first, onClick = { category = item.first }, label = { Text(item.second) })
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 120) title = it },
                    label = { Text("标题") },
                    supportingText = { Text("简洁说明问题或建议（4～120 字）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = content,
                    onValueChange = { if (it.length <= 5000) content = it },
                    label = { Text("详细描述") },
                    placeholder = { Text("发生了什么？你希望怎样改进？如能复现，请写清操作步骤。") },
                    supportingText = { Text("${content.length}/5000") },
                    minLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("图片附件（可选）", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "最多 4 张，单张不超过 8MB",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                imageError = null
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            enabled = !readingImages && !state.submitting && selectedImages.size < MAX_FEEDBACK_IMAGES,
                        ) {
                            if (readingImages) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("读取中…", modifier = Modifier.padding(start = 6.dp))
                            } else Text(if (selectedImages.isEmpty()) "上传图片" else "继续添加")
                        }
                    }
                    Text(
                        "仅在点击后打开系统图片选择器，并只读取你选中的图片。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (selectedImages.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 10.dp),
                        ) {
                            items(selectedImages, key = { it.uri.toString() }) { image ->
                                Box(Modifier.size(86.dp)) {
                                    AsyncImage(
                                        model = image.uri,
                                        contentDescription = "待上传反馈图片",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                                    )
                                    Box(
                                        Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp)
                                            .clip(CircleShape).background(Color(0xB3000000))
                                            .clickable(enabled = !state.submitting) {
                                                selectedImages = selectedImages.filterNot { it.uri == image.uri }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("×", color = Color.White, fontSize = 18.sp, lineHeight = 18.sp)
                                    }
                                }
                            }
                        }
                    }
                    imageError?.let {
                        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 7.dp))
                    }
                }
            }
            state.error?.let { message ->
                item { Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
            }
            item {
                Button(
                    onClick = { onSubmit(title, content, category, selectedImages.map { it.upload }) },
                    enabled = !state.submitting && !readingImages,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Text("提交中…", modifier = Modifier.padding(start = 8.dp))
                    } else Text("提交 Issue")
                }
            }
        }
    }
}

@Composable
private fun FeedbackDetail(
    modifier: Modifier,
    state: FeedbackViewModel.UiState,
    onBack: () -> Unit,
    onComment: (String, Long?) -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
) {
    val issue = state.detail
    var reply by remember(issue?.id, issue?.commentCount) { mutableStateOf("") }
    var replyTarget by remember(issue?.id, issue?.commentCount) { mutableStateOf<FeedbackCommentDto?>(null) }
    var showReplyConfirm by remember { mutableStateOf(false) }
    var showCloseConfirm by remember { mutableStateOf(false) }
    var previewImage by remember(issue?.id) { mutableStateOf<String?>(null) }
    val visibleReplyCounts = remember(issue?.id) { mutableStateMapOf<Long, Int>() }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FeedbackTopBar(
            issue?.let { "Issue #${it.id}" } ?: "反馈详情",
            onBack,
        ) {
            if (issue?.canClose == true && issue.status == "OPEN") {
                TextButton(
                    onClick = { showCloseConfirm = true },
                    enabled = !state.closing && !state.submitting,
                ) {
                    Text(if (state.closing) "关闭中…" else "关闭")
                }
            }
        }
        when {
            state.detailLoading -> Box(Modifier.weight(1f).fillMaxWidth()) { LoadingBox() }
            issue == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyOrError(state.error ?: "反馈加载失败", "重试", onRetry)
            }
            else -> {
                val canReply = issue.status == "OPEN" && issue.canComment
                val threads = remember(issue.comments) { feedbackCommentThreads(issue.comments) }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface).padding(16.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusPill(issue.status)
                                CategoryPill(issue.categoryText, issue.category, Modifier.padding(start = 7.dp))
                            }
                            Text(issue.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                                lineHeight = 28.sp, modifier = Modifier.padding(top = 13.dp))
                            Text(
                                "${issue.reporterName ?: "用户"} 提交于 ${formatDate(issue.createdAt)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 7.dp),
                            )
                            Box(Modifier.fillMaxWidth().padding(vertical = 14.dp).height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant))
                            Text(issue.content, fontSize = 15.sp, lineHeight = 23.sp)
                            if (issue.images.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                                    modifier = Modifier.padding(top = 14.dp),
                                ) {
                                    items(issue.images, key = { it }) { url ->
                                        AsyncImage(
                                            model = url,
                                            contentDescription = "反馈图片",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(92.dp).clip(RoundedCornerShape(10.dp))
                                                .clickable { previewImage = url },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (issue.status == "CLOSED") {
                        item {
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
                            ) {
                                Text("✓ 该 Issue 已关闭", fontWeight = FontWeight.Medium)
                                Text(
                                    "由 ${issue.closedByName ?: "用户"} 关闭于 ${formatDate(issue.closedAt)}。如问题仍存在，请新建反馈并引用 #${issue.id}。",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                    item { Text("讨论时间线 · ${issue.commentCount} 条回复", fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                    if (issue.comments.isEmpty()) {
                        item { EmptyOrError("暂时还没有回复", null, null) }
                    } else {
                        items(threads, key = { it.root.id }) { thread ->
                            val shown = visibleReplyCounts[thread.root.id] ?: FEEDBACK_REPLY_PAGE_SIZE
                            CommentThreadCard(
                                thread = thread,
                                visibleReplies = shown,
                                canReply = canReply,
                                onReply = {
                                    replyTarget = it
                                    showReplyConfirm = false
                                },
                                onShowMore = {
                                    visibleReplyCounts[thread.root.id] =
                                        nextVisibleReplyCount(shown, thread.replies.size)
                                },
                            )
                        }
                    }
                    state.error?.let { message ->
                        item { Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
                    }
                }
                if (canReply) {
                    Column(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        replyTarget?.let { target ->
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "正在回复 ${target.authorName ?: "用户"}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "取消",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clickable { replyTarget = null }.padding(4.dp),
                                )
                            }
                        }
                        OutlinedTextField(
                            value = reply,
                            onValueChange = { if (it.length <= 2000) reply = it },
                            placeholder = {
                                Text(
                                    replyTarget?.authorName?.let { "回复 @$it…" }
                                        ?: "补充信息或回复讨论…",
                                )
                            },
                            minLines = 2,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { if (reply.isNotBlank()) showReplyConfirm = true },
                            enabled = reply.isNotBlank() && !state.submitting && !state.closing,
                            modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
                        ) { Text(if (state.submitting) "发布中…" else "发布回复") }
                    }
                }
            }
        }
    }
    if (showReplyConfirm) {
        AlertDialog(
            onDismissRequest = { showReplyConfirm = false },
            title = { Text("发布回复") },
            text = {
                Text(
                    replyTarget?.authorName?.let { "将回复 @$it，内容会展示在其回复下方，确定发布？" }
                        ?: "回复将展示在此 Issue 的共享时间线中，确定发布？",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReplyConfirm = false
                        onComment(reply, replyTarget?.id)
                    },
                ) { Text("发布") }
            },
            dismissButton = { TextButton(onClick = { showReplyConfirm = false }) { Text("取消") } },
        )
    }
    if (showCloseConfirm && issue != null) {
        AlertDialog(
            onDismissRequest = { if (!state.closing) showCloseConfirm = false },
            title = { Text("关闭 Issue #${issue.id}") },
            text = { Text("关闭后不能继续回复，但不会删除反馈正文、图片或讨论记录。确定关闭？") },
            confirmButton = {
                TextButton(
                    enabled = !state.closing,
                    onClick = {
                        showCloseConfirm = false
                        onClose()
                    },
                ) { Text("确认关闭") }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.closing,
                    onClick = { showCloseConfirm = false },
                ) { Text("取消") }
            },
        )
    }
    previewImage?.let { url ->
        AlertDialog(
            onDismissRequest = { previewImage = null },
            title = { Text("反馈图片") },
            text = {
                AsyncImage(
                    model = url,
                    contentDescription = "反馈图片大图",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 560.dp),
                )
            },
            confirmButton = { TextButton(onClick = { previewImage = null }) { Text("关闭") } },
        )
    }
}

@Composable
private fun CommentThreadCard(
    thread: FeedbackCommentThread,
    visibleReplies: Int,
    canReply: Boolean,
    onReply: (FeedbackCommentDto) -> Unit,
    onShowMore: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        CommentCard(thread.root, nested = false, canReply = canReply, onReply = onReply)
        if (thread.replies.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                thread.replies.take(visibleReplies).forEach { reply ->
                    CommentCard(reply, nested = true, canReply = canReply, onReply = onReply)
                }
                if (visibleReplies < thread.replies.size) {
                    TextButton(onClick = onShowMore) {
                        Text("展开更多回复（剩余 ${thread.replies.size - visibleReplies} 条）")
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentCard(
    comment: FeedbackCommentDto,
    nested: Boolean,
    canReply: Boolean,
    onReply: (FeedbackCommentDto) -> Unit,
) {
    val background = if (comment.adminReply) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    else if (nested) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    else MaterialTheme.colorScheme.surface
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(if (nested) 10.dp else 12.dp))
            .background(background).padding(if (nested) 12.dp else 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(if (nested) 28.dp else 32.dp).clip(CircleShape).background(
                    if (comment.adminReply) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondaryContainer,
                ),
                contentAlignment = Alignment.Center,
            ) {
                val avatar = comment.authorAvatar?.takeIf { it.startsWith("http") }
                if (avatar != null) {
                    AsyncImage(
                        model = avatar,
                        contentDescription = "${comment.authorName ?: "用户"}的头像",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        (comment.authorName ?: "用").take(1),
                        fontSize = 12.sp,
                        color = if (comment.adminReply) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        comment.authorName ?: "用户",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (comment.adminReply) {
                        CommentRolePill("维护者", maintainer = true)
                    }
                    if (comment.questioner) {
                        CommentRolePill("提问者", maintainer = false)
                    }
                }
                Text(shortDate(comment.createdAt), fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
        Text(comment.content, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 10.dp))
        if (canReply) {
            Text(
                "回复",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.End).clickable { onReply(comment) }
                    .padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun CommentRolePill(text: String, maintainer: Boolean) {
    val background = if (maintainer) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.tertiaryContainer
    val foreground = if (maintainer) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onTertiaryContainer
    Box(
        Modifier.padding(start = 7.dp).clip(RoundedCornerShape(999.dp))
            .background(background).padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(text, fontSize = 9.sp, color = foreground)
    }
}

internal data class FeedbackCommentThread(
    val root: FeedbackCommentDto,
    val replies: List<FeedbackCommentDto>,
)

internal const val FEEDBACK_REPLY_PAGE_SIZE = 5

/** 将后端的一层 parentId 时间线分组；父回复缺失时保留为根回复，避免历史数据消失。 */
internal fun feedbackCommentThreads(comments: List<FeedbackCommentDto>): List<FeedbackCommentThread> {
    val rootIds = comments.asSequence().filter { it.parentId == null }.map { it.id }.toSet()
    val roots = comments.filter { it.parentId == null || it.parentId !in rootIds }
    val replies = comments.filter { it.parentId in rootIds }.groupBy { it.parentId }
    return roots.map { root -> FeedbackCommentThread(root, replies[root.id].orEmpty()) }
}

internal fun nextVisibleReplyCount(current: Int, total: Int): Int =
    (current.coerceAtLeast(0) + FEEDBACK_REPLY_PAGE_SIZE).coerceAtMost(total.coerceAtLeast(0))

@Composable
private fun FeedbackTopBar(title: String, onBack: () -> Unit, action: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(AppIcons.Back, "返回") }
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        action()
    }
}

@Composable
private fun StatusPill(status: String) {
    val open = status == "OPEN"
    val color = if (open) Color(0xFF16A34A) else MaterialTheme.colorScheme.outline
    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = 0.12f))
        .padding(horizontal = 9.dp, vertical = 4.dp)) {
        Text(if (open) "开放" else "已关闭", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun CategoryPill(text: String, category: String, modifier: Modifier = Modifier) {
    val color = when (category) {
        "BUG" -> Color(0xFFDC2626)
        "FEATURE" -> Color(0xFF2563EB)
        "EXPERIENCE" -> Color(0xFFD97706)
        else -> MaterialTheme.colorScheme.outline
    }
    Box(modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = 0.1f))
        .padding(horizontal = 9.dp, vertical = 4.dp)) {
        Text(text, fontSize = 10.sp, color = color)
    }
}

@Composable
private fun EmptyOrError(message: String, action: String?, onClick: (() -> Unit)?) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null && onClick != null) {
            OutlinedButton(onClick = onClick, modifier = Modifier.padding(top = 10.dp)) { Text(action) }
        }
    }
}

private fun shortDate(value: String?): String {
    if (value.isNullOrBlank()) return "-"
    return value.replace('T', ' ').take(16)
}

private fun formatDate(value: String?): String = shortDate(value)

/** 把 Photo Picker 授予的临时 URI 读取成待上传数据；不扫描相册，也不保留额外媒体权限。 */
private suspend fun prepareFeedbackImages(
    context: Context,
    uris: List<Uri>,
): List<SelectedFeedbackImage> = withContext(Dispatchers.IO) {
    uris.mapIndexed { index, uri ->
        val mime = context.contentResolver.getType(uri)?.lowercase()
            ?: throw IllegalArgumentException("无法识别所选图片格式")
        val extension = when (mime) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            else -> throw IllegalArgumentException("仅支持 JPG、PNG、GIF 或 WebP 图片")
        }
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("无法读取所选图片")
        val bytes = input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_FEEDBACK_IMAGE_BYTES) {
                    throw IllegalArgumentException("单张图片不能超过 8MB")
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        if (bytes.isEmpty()) throw IllegalArgumentException("所选图片内容为空")
        SelectedFeedbackImage(
            uri = uri,
            upload = FeedbackImageUpload(
                bytes = bytes,
                filename = "feedback_${System.currentTimeMillis()}_${index + 1}.$extension",
                mediaType = if (mime == "image/jpg") "image/jpeg" else mime,
            ),
        )
    }
}
