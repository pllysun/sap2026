package edu.csuft.sap.data.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 全局「后端可达性」状态（在线/离线兜底）。
 * 由 AppViewModel 在进场/切回前台探测后端后设置。
 * - online=true：后端可达 → 正常在线（按会员/手选模式）。
 * - online=false：后端不可达（宕机/没网）→ 离线模式：强制 [AppMode.WEB] 只看本地课表、隐藏所有在线功能，
 *   且**绝不清登录态**；待下次进场重新探到可达即自动恢复在线。
 */
object ConnectivityState {
    /** 由 AppViewModel 在探测后端后设置。true=在线，false=离线兜底。 */
    var online by mutableStateOf(true)
}
