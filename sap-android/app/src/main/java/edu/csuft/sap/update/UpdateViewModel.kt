package edu.csuft.sap.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.csuft.sap.BuildConfig
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.dto.AppVersionDto
import edu.csuft.sap.di.Graph
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** 升级流程状态。Idle 时不展示任何 UI。 */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class CheckFailed(val message: String) : UpdateUiState
    data class Available(val info: AppVersionDto, val forced: Boolean) : UpdateUiState
    data class Downloading(val info: AppVersionDto, val forced: Boolean, val progress: Float) : UpdateUiState
    data class Downloaded(val info: AppVersionDto, val forced: Boolean, val file: File) : UpdateUiState
    data class DownloadFailed(val info: AppVersionDto, val forced: Boolean, val message: String) : UpdateUiState
}

/**
 * 应用内升级编排：检查 → 提示 → 下载(进度) → 校验 → 侧载安装。
 * 自动检查(auto)在“无更新/失败”时静默回 Idle；手动检查会提示“已是最新/检查失败”。
 */
class UpdateViewModel : ViewModel() {

    private val repo = Graph.updateRepository
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    fun check(manual: Boolean) {
        val s = _state.value
        // 已在更新流程中（提示/下载/下载完成）则不重复检查
        if (s is UpdateUiState.Available || s is UpdateUiState.Downloading || s is UpdateUiState.Downloaded) return
        if (manual) _state.value = UpdateUiState.Checking
        viewModelScope.launch {
            when (val r = repo.latest()) {
                is Outcome.Success -> {
                    val info = r.data
                    val cur = BuildConfig.VERSION_CODE
                    if (info.versionCode <= cur) {
                        _state.value = if (manual) UpdateUiState.UpToDate else UpdateUiState.Idle
                    } else {
                        val forced = info.forceUpdate || cur < info.minSupportedVersionCode
                        _state.value = UpdateUiState.Available(info, forced)
                    }
                }
                is Outcome.Error ->
                    _state.value = if (manual) UpdateUiState.CheckFailed(r.message) else UpdateUiState.Idle
            }
        }
    }

    fun download(context: Context) {
        val s = _state.value
        val info = when (s) {
            is UpdateUiState.Available -> s.info
            is UpdateUiState.DownloadFailed -> s.info
            else -> return
        }
        val forced = when (s) {
            is UpdateUiState.Available -> s.forced
            is UpdateUiState.DownloadFailed -> s.forced
            else -> false
        }
        val appContext = context.applicationContext
        downloadJob?.cancel()
        _state.value = UpdateUiState.Downloading(info, forced, 0f)
        downloadJob = viewModelScope.launch {
            try {
                val file = repo.download(appContext, info) { p ->
                    _state.value = UpdateUiState.Downloading(info, forced, p)
                }
                _state.value = UpdateUiState.Downloaded(info, forced, file)
            } catch (e: Throwable) {
                _state.value = UpdateUiState.DownloadFailed(info, forced, e.message ?: "下载失败，请重试")
            }
        }
    }

    /** 安装：若未授权“未知来源”，先跳授权页（用户授权后返回再点一次安装）。 */
    fun install(context: Context) {
        val s = _state.value as? UpdateUiState.Downloaded ?: return
        if (!ApkInstaller.canInstall(context)) {
            ApkInstaller.requestInstallPermission(context)
            return
        }
        ApkInstaller.install(context, s.file)
    }

    /** 关闭非强制更新的弹窗。 */
    fun dismiss() {
        downloadJob?.cancel()
        _state.value = UpdateUiState.Idle
    }
}
