package edu.csuft.sap.ui.profile

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
internal fun ProfileAvatar(
    bitmap: Bitmap?,
    name: String?,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
    previewUrl: String? = null,
) {
    Box(modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            Image(image, "头像", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        } else {
            Text(name?.takeIf(String::isNotBlank)?.take(1) ?: "会", fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        // 仅编辑页的未保存头像使用异步预览，加载中和失败时仍保留下面的原头像。
        if (previewUrl != null) {
            val context = LocalContext.current
            val request = remember(context, previewUrl, bitmap) {
                val previous = bitmap?.let { BitmapDrawable(context.resources, it) }
                ImageRequest.Builder(context).data(previewUrl)
                    .placeholder(previous).error(previous).crossfade(false).build()
            }
            AsyncImage(request, "头像预览", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
    }
}
