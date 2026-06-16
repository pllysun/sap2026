package edu.csuft.sap.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import edu.csuft.sap.ui.icons.AppIcons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.csuft.sap.ui.theme.ThemeState

/** 主题色（辅色）设置：预设色板 + 取色盘自定义。简约白为主，辅色用于全局强调。 */
@Composable
fun ThemeScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val accent = ThemeState.accent
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar("主题色", onBack)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {

            // 预览
            ThemeCard {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).background(accent, CircleShape))
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text("辅色预览", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("当前 ${hexOf(accent)}", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    }
                    Box(
                        Modifier.background(accent, RoundedCornerShape(999.dp)).padding(horizontal = 16.dp, vertical = 7.dp),
                    ) { Text("按钮", fontSize = 13.sp, color = onColorOf(accent)) }
                }
            }

            SectionLabel("预设")
            ThemeCard {
                Column(Modifier.padding(12.dp)) {
                    ThemeState.PRESETS.chunked(5).forEach { rowItems ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            rowItems.forEach { (name, color) ->
                                Swatch(
                                    name = name,
                                    color = color,
                                    selected = sameColor(color, accent),
                                    onClick = { ThemeState.setAccent(context, color) },
                                )
                            }
                        }
                    }
                }
            }

            SectionLabel("自定义")
            ThemeCard {
                Row(
                    Modifier.fillMaxWidth().clickable { showPicker = true }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(24.dp).background(accent, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                    Text("取色盘自定义…", fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp).weight(1f))
                    Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.outline)
                }
            }

            Text(
                "辅色用于全局强调：底栏选中、按钮、开关、今日高亮、课程编辑里的选周等。主色保持简约白。",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp), lineHeight = 18.sp,
            )
        }
    }

    if (showPicker) {
        ColorPickerDialog(
            initial = accent,
            onConfirm = { ThemeState.setAccent(context, it); showPicker = false },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun Swatch(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 2.dp),
    ) {
        Box(
            Modifier.size(44.dp).background(color, CircleShape)
                .then(if (selected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(AppIcons.Check, contentDescription = "已选", tint = onColorOf(color), modifier = Modifier.size(22.dp))
        }
        Text(name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
    }
}

// ---------- 取色盘 ----------

@Composable
private fun ColorPickerDialog(initial: Color, onConfirm: (Color) -> Unit, onDismiss: () -> Unit) {
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color.hsv(hue, sat, value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("取色盘") },
        text = {
            Column {
                SatValPanel(
                    hue = hue, sat = sat, value = value,
                    onChange = { s, v -> sat = s; value = v },
                    modifier = Modifier.fillMaxWidth().height(176.dp),
                )
                Spacer(Modifier.height(16.dp))
                HueBar(hue = hue, onChange = { hue = it }, modifier = Modifier.fillMaxWidth().height(24.dp))
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).background(color, RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(hexOf(color), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(color) }) { Text("使用") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SatValPanel(hue: Float, sat: Float, value: Float, onChange: (Float, Float) -> Unit, modifier: Modifier) {
    val hueColor = Color.hsv(hue, 1f, 1f)
    var size by remember { mutableStateOf(IntSize.Zero) }
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos -> emitSV(pos, size, onChange) },
                ) { change, _ -> emitSV(change.position, size, onChange) }
            }
            .pointerInput(Unit) {
                detectTapGestures { pos -> emitSV(pos, size, onChange) }
            },
    ) {
        drawRect(brush = Brush.horizontalGradient(listOf(Color.White, hueColor)))
        drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val cx = (sat * this.size.width).coerceIn(0f, this.size.width)
        val cy = ((1f - value) * this.size.height).coerceIn(0f, this.size.height)
        drawCircle(Color.Black.copy(alpha = 0.45f), radius = 9.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 4.dp.toPx()))
        drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 2.5.dp.toPx()))
    }
}

@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit, modifier: Modifier) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val hueColors = remember { (0..6).map { Color.hsv(it * 60f, 1f, 1f) } }
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos -> emitHue(pos.x, size, onChange) },
                ) { change, _ -> emitHue(change.position.x, size, onChange) }
            }
            .pointerInput(Unit) {
                detectTapGestures { pos -> emitHue(pos.x, size, onChange) }
            },
    ) {
        drawRect(brush = Brush.horizontalGradient(hueColors))
        val x = ((hue / 360f) * this.size.width).coerceIn(0f, this.size.width)
        val r = this.size.height / 2f - 1.5.dp.toPx()
        drawCircle(Color.Black.copy(alpha = 0.4f), radius = r + 1.dp.toPx(), center = Offset(x, this.size.height / 2f), style = Stroke(width = 3.dp.toPx()))
        drawCircle(Color.White, radius = r, center = Offset(x, this.size.height / 2f), style = Stroke(width = 2.5.dp.toPx()))
    }
}

private fun emitSV(pos: Offset, size: IntSize, onChange: (Float, Float) -> Unit) {
    if (size.width <= 0 || size.height <= 0) return
    val s = (pos.x / size.width).coerceIn(0f, 1f)
    val v = (1f - pos.y / size.height).coerceIn(0f, 1f)
    onChange(s, v)
}

private fun emitHue(x: Float, size: IntSize, onChange: (Float) -> Unit) {
    if (size.width <= 0) return
    onChange((x / size.width).coerceIn(0f, 1f) * 360f)
}

// ---------- 共用 ----------

private fun hexOf(c: Color): String = "#%06X".format(0xFFFFFF and c.toArgb())

private fun onColorOf(c: Color): Color = if (c.luminance() > 0.55f) Color(0xFF1F2329) else Color.White

/** 量化到 ARGB 比较，避免浮点误差导致选中态判断失败。 */
private fun sameColor(a: Color, b: Color): Boolean = a.toArgb() == b.toArgb()

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(AppIcons.Back, "返回") }
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ThemeCard(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))) { content() }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp, start = 4.dp))
}
