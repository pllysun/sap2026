package edu.csuft.sap.ui.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuft.sap.R

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    vm: AuthViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    var studentId by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var pwVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.success) { if (state.success) onLoggedIn() }

    val primary = MaterialTheme.colorScheme.primary
    val primaryDark = lerp(primary, Color.Black, 0.22f)
    val surface = MaterialTheme.colorScheme.surface

    // 入场动画：整体淡入 + 轻微上浮
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val enter by animateFloatAsState(if (shown) 1f else 0f, tween(620), label = "enter")

    Box(Modifier.fillMaxSize().background(surface)) {
        // —— 背景：简约白 + 天蓝极光光斑（克制、高级）——
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        primary.copy(alpha = 0.10f),
                        surface,
                        primary.copy(alpha = 0.06f),
                    ),
                ),
            ),
        )
        Box(
            Modifier.align(Alignment.TopEnd).size(380.dp).offset(x = 140.dp, y = (-150).dp)
                .background(Brush.radialGradient(listOf(primary.copy(alpha = 0.28f), Color.Transparent))),
        )
        Box(
            Modifier.align(Alignment.BottomStart).size(340.dp).offset(x = (-120).dp, y = 120.dp)
                .background(Brush.radialGradient(listOf(primary.copy(alpha = 0.16f), Color.Transparent))),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 28.dp)
                .graphicsLayer {
                    alpha = enter
                    translationY = (1f - enter) * 48.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(96.dp))

            // Logo + 柔和光晕
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(132.dp)
                        .background(Brush.radialGradient(listOf(primary.copy(alpha = 0.35f), Color.Transparent))),
                )
                Image(
                    painter = painterResource(R.drawable.app_logo),
                    contentDescription = "软协课表",
                    modifier = Modifier
                        .size(88.dp)
                        .shadow(18.dp, RoundedCornerShape(24.dp), spotColor = primary.copy(alpha = 0.5f))
                        .clip(RoundedCornerShape(24.dp)),
                )
            }

            Text(
                text = "软协课表",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                text = "CSUFT 软件协会 · 教务助手",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(30.dp))

            // —— 玻璃质感卡片 ——
            Column(
                Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 22.dp,
                        shape = RoundedCornerShape(26.dp),
                        ambientColor = primary.copy(alpha = 0.12f),
                        spotColor = primary.copy(alpha = 0.16f),
                    )
                    .clip(RoundedCornerShape(26.dp))
                    .background(surface)
                    .padding(horizontal = 22.dp, vertical = 26.dp),
            ) {
                Text(
                    "会员登录",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "用协会会员账号登录，解锁全部功能",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
                )

                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedLeadingIconColor = primary,
                    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                )

                OutlinedTextField(
                    value = studentId,
                    onValueChange = { studentId = it; if (state.error != null) vm.clearError() },
                    label = { Text("学号") },
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; if (state.error != null) vm.clearError() },
                    label = { Text("密码") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { pwVisible = !pwVisible }) {
                            Icon(
                                if (pwVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (pwVisible) "隐藏密码" else "显示密码",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    visualTransformation = if (pwVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                )

                // 错误提示（固定高度避免按钮跳动）
                Box(Modifier.fillMaxWidth().height(22.dp).padding(top = 4.dp)) {
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }

                // —— 渐变登录按钮 ——
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(52.dp)
                        .shadow(12.dp, RoundedCornerShape(14.dp), spotColor = primary.copy(alpha = 0.55f))
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(primary, primaryDark)))
                        .clickable(
                            enabled = !state.loading,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { vm.login(studentId, password) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(
                            "登 录",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "登录凭证安全保存在本机，下次自动免密进入",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 40.dp),
            )
        }
    }
}
