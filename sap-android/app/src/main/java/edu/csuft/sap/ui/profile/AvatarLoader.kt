package edu.csuft.sap.ui.profile

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Activity 内保留解码后的头像，切换底栏无需重新创建异步图片请求。 */
internal class AvatarLoader(context: Context) {
    private val context = context.applicationContext
    private val retained = RetainedAvatar<Bitmap> { previous, next -> previous.sameAs(next) }
    val image = retained.image

    fun activate(account: String) = retained.activate(account)

    suspend fun load(account: String, url: String?) = retained.load(account, url) {
        withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(384) // 同一张位图供 48dp 信息卡和 96dp 编辑页使用，限制常驻内存。
                .allowHardware(false) // 可比较像素，资料改动但图片没变时保留原位图。
                .memoryCacheKey("profile-avatar:$account:$url")
                .crossfade(false)
                .build()
            (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
        }
    }
}
