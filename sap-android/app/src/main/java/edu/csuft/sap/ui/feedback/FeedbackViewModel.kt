package edu.csuft.sap.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.csuft.sap.BuildConfig
import edu.csuft.sap.data.account.CurrentAccount
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.dto.FeedbackIssueDto
import edu.csuft.sap.data.repository.FeedbackImageUpload
import edu.csuft.sap.di.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** App 内嵌 Issue 中心状态：列表筛选、创建、详情与用户跟进。 */
class FeedbackViewModel : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val detailLoading: Boolean = false,
        val submitting: Boolean = false,
        val closing: Boolean = false,
        val issues: List<FeedbackIssueDto> = emptyList(),
        val total: Long = 0,
        val status: String = "OPEN",
        val category: String = "ALL",
        val keyword: String = "",
        val mine: Boolean = false,
        val detailId: Long? = null,
        val detail: FeedbackIssueDto? = null,
        val error: String? = null,
        val notice: String? = null,
        val quotaMessage: String? = null,
    )

    private val repository = Graph.feedbackRepository
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var listGeneration = 0
    private var sessionGeneration = 0

    init {
        refresh()
        // Activity 内 ViewModel 会跨登出/换号复用；账号变化时必须清空旧账号的 Issue 内存态。
        viewModelScope.launch {
            CurrentAccount.uid.drop(1).collect { account ->
                sessionGeneration++
                listGeneration++
                _state.value = UiState()
                if (account != null) refresh()
            }
        }
    }

    fun refresh() {
        val snapshot = _state.value
        val generation = ++listGeneration
        val session = sessionGeneration
        _state.value = snapshot.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val result = repository.issues(
                snapshot.status,
                snapshot.category,
                snapshot.keyword.trim().ifBlank { null },
                snapshot.mine,
            )) {
                is Outcome.Success -> if (session == sessionGeneration && generation == listGeneration) {
                    _state.value = _state.value.copy(
                        loading = false,
                        issues = result.data.records,
                        total = result.data.total,
                        error = null,
                    )
                }
                is Outcome.Error -> if (session == sessionGeneration && generation == listGeneration) {
                    _state.value = _state.value.copy(loading = false, error = result.message)
                }
            }
        }
    }

    fun setStatus(value: String) {
        if (value == _state.value.status) return
        _state.value = _state.value.copy(status = value)
        refresh()
    }

    fun setCategory(value: String) {
        if (value == _state.value.category) return
        _state.value = _state.value.copy(category = value)
        refresh()
    }

    fun setMine(value: Boolean) {
        if (value == _state.value.mine) return
        _state.value = _state.value.copy(mine = value)
        refresh()
    }

    fun search(keyword: String) {
        _state.value = _state.value.copy(keyword = keyword)
        refresh()
    }

    fun open(id: Long) {
        val session = sessionGeneration
        _state.value = _state.value.copy(detailId = id, detail = null, detailLoading = true, error = null)
        viewModelScope.launch {
            when (val result = repository.detail(id)) {
                is Outcome.Success -> if (session == sessionGeneration && _state.value.detailId == id) {
                    _state.value = _state.value.copy(
                        detailLoading = false, detail = result.data, error = null)
                }
                is Outcome.Error -> if (session == sessionGeneration && _state.value.detailId == id) {
                    _state.value = _state.value.copy(
                        detailLoading = false, error = result.message)
                }
            }
        }
    }

    fun create(title: String, content: String, category: String,
               images: List<FeedbackImageUpload>, onCreated: () -> Unit) {
        val cleanTitle = title.trim()
        val cleanContent = content.trim()
        when {
            cleanTitle.length !in 4..120 -> return fail("标题须为 4～120 个字符")
            cleanContent.length !in 10..5000 -> return fail("请用 10～5000 个字符描述问题或建议")
            _state.value.submitting -> return
        }
        val session = sessionGeneration
        _state.value = _state.value.copy(submitting = true, error = null)
        viewModelScope.launch {
            when (val result = repository.create(
                cleanTitle,
                cleanContent,
                category,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
                images,
            )) {
                is Outcome.Success -> if (session == sessionGeneration) {
                    _state.value = _state.value.copy(
                        submitting = false,
                        detailId = result.data.id,
                        detail = result.data,
                        notice = "反馈已提交",
                    )
                    onCreated()
                    refreshAfterMutation()
                }
                is Outcome.Error -> if (session == sessionGeneration) {
                    _state.value = if (result.code == 429) {
                        _state.value.copy(
                            submitting = false,
                            error = null,
                            quotaMessage = result.message,
                        )
                    } else {
                        _state.value.copy(submitting = false, error = result.message)
                    }
                }
            }
        }
    }

    fun comment(content: String, parentId: Long?) {
        val issue = _state.value.detail ?: return
        if (issue.status != "OPEN" || !issue.canComment) {
            return fail("该反馈已关闭，如问题仍存在请新建反馈")
        }
        val value = content.trim()
        if (value.isBlank()) return fail("请输入回复内容")
        if (value.length > 2000) return fail("回复内容不能超过 2000 个字符")
        if (_state.value.submitting) return
        val session = sessionGeneration
        _state.value = _state.value.copy(submitting = true, error = null)
        viewModelScope.launch {
            when (val result = repository.comment(issue.id, value, parentId)) {
                is Outcome.Success -> if (session == sessionGeneration) {
                    val current = _state.value
                    val stillOpen = current.detailId == issue.id
                    _state.value = current.copy(
                        submitting = false,
                        detail = if (stillOpen) result.data else current.detail,
                        notice = "回复已发布",
                    )
                    refreshAfterMutation()
                }
                is Outcome.Error -> if (session == sessionGeneration) {
                    val current = _state.value
                    _state.value = current.copy(
                        submitting = false,
                        error = if (current.detailId == issue.id) result.message else current.error,
                    )
                }
            }
        }
    }

    fun closeIssue() {
        val issue = _state.value.detail ?: return
        if (!issue.canClose || issue.status != "OPEN" || _state.value.closing) return
        val session = sessionGeneration
        _state.value = _state.value.copy(closing = true, error = null)
        viewModelScope.launch {
            when (val result = repository.close(issue.id)) {
                is Outcome.Success -> if (session == sessionGeneration) {
                    val current = _state.value
                    _state.value = current.copy(
                        closing = false,
                        detail = if (current.detailId == issue.id) result.data else current.detail,
                        notice = "Issue 已关闭",
                    )
                    refreshAfterMutation()
                }
                is Outcome.Error -> if (session == sessionGeneration) {
                    val current = _state.value
                    _state.value = current.copy(
                        closing = false,
                        error = if (current.detailId == issue.id) result.message else current.error,
                    )
                }
            }
        }
    }

    fun clearDetail() {
        _state.value = _state.value.copy(detailId = null, detail = null, detailLoading = false, error = null)
    }

    fun consumeNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    fun consumeQuotaMessage() {
        _state.value = _state.value.copy(quotaMessage = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun refreshAfterMutation() {
        val snapshot = _state.value
        val generation = ++listGeneration
        val session = sessionGeneration
        viewModelScope.launch {
            when (val result = repository.issues(
                snapshot.status, snapshot.category,
                snapshot.keyword.trim().ifBlank { null }, snapshot.mine,
            )) {
                is Outcome.Success -> if (session == sessionGeneration && generation == listGeneration) {
                    _state.value = _state.value.copy(
                        loading = false, issues = result.data.records, total = result.data.total)
                }
                is Outcome.Error -> Unit
            }
        }
    }

    private fun fail(message: String) {
        _state.value = _state.value.copy(error = message)
    }
}
