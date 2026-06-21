package edu.csuft.sap.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.csuft.sap.data.remote.dto.IdentityDto

/**
 * 头像显示 URL（带缓存破坏）：附 `?v=<updatedAt>`。
 * 头像常存在固定 URL（如 …/headerShot.jpg）原地覆盖，URL 不变时 Coil 会一直用磁盘缓存的旧图；
 * 资料修改时间 [version] 变化即让 URL 变化 → Coil 重新加载新图。非 http 链接返回 null（不展示）。
 */
fun avatarUrlOf(url: String?, version: Long?): String? {
    val u = url?.takeIf { it.startsWith("http") } ?: return null
    if (version == null) return u
    return u + (if (u.contains("?")) "&" else "?") + "v=" + version
}

/**
 * 由换届身份(届+职务) + 角色码 推导「平台身份」标签：
 * - 有换届记录：**只取届(年份)最大的那一条**，格式「{届}届{职务}」，如 2022届会长 / 2025届宣传部部长
 *   （职务"成员"展示为"正式成员"，即 2025届正式成员）。
 * - 无换届记录：按角色码兜底——0超级管理员 / 1会长 / 2管理员 / 3正式成员 / 其它(含游客4或无角色)=游客。
 *
 * 返回单元素列表（UI 用 [IdentityTags] 渲染单个药丸）。
 */
fun identityLabels(identities: List<IdentityDto>, roles: List<Int>): List<String> {
    // 取届(年份)最大的一条有效身份
    val latest = identities
        .filter { !it.positionName.isNullOrBlank() }
        .maxByOrNull { it.grade?.filter(Char::isDigit)?.toIntOrNull() ?: Int.MIN_VALUE }
    if (latest != null) {
        val pos = latest.positionName!!.let { if (it == "成员") "正式成员" else it }
        val g = latest.grade?.takeIf { it.isNotBlank() }
        return listOf(if (g != null) "${g}届${pos}" else pos) // 年份与身份之间加「届」
    }
    val label = when (roles.minOrNull()) {
        0 -> "超级管理员"
        1 -> "会长"
        2 -> "管理员"
        3 -> "正式成员"
        else -> "游客"
    }
    return listOf(label)
}

/** 平台身份标签云（小药丸）。labels 为空时不渲染。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdentityTags(labels: List<String>, modifier: Modifier = Modifier) {
    if (labels.isEmpty()) return
    FlowRow(modifier = modifier) {
        labels.forEach { label ->
            Text(
                label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .padding(end = 6.dp, top = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
