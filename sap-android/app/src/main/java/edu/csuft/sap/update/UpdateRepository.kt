package edu.csuft.sap.update

import android.content.Context
import edu.csuft.sap.data.remote.ApiService
import edu.csuft.sap.data.remote.Outcome
import edu.csuft.sap.data.remote.apiData
import edu.csuft.sap.data.remote.dto.AppVersionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * 应用内升级数据层：
 * - [latest] 拉版本元数据（走后端 /api/app/latest，需登录；下载地址只对会员下发）。
 * - [download] 经后端计量重定向端点 /api/file/go 下载：后端记一笔下载流量并 302 到 COS/CDN 直链
 *   （真实字节仍走 CDN，不经自家服务器），带 sap-token 以按会员归属流量；边下边算 SHA-256 校验完整性。
 */
class UpdateRepository(
    private val api: ApiService,
    private val baseUrl: String,
    private val tokenProvider: () -> String?,
) {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun latest(): Outcome<AppVersionDto> = apiData { api.appLatest() }

    /**
     * 下载 APK 到 getExternalFilesDir/update/，进度回调 0f..1f；下完校验 SHA-256。
     * 成功返回文件，失败抛异常（消息可直接展示）。
     */
    suspend fun download(
        context: Context,
        info: AppVersionDto,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val cosUrl = info.downloadUrl?.takeIf { it.isNotBlank() } ?: error("下载地址为空，请联系管理员")
        val dir = File(context.getExternalFilesDir(null), "update").apply { mkdirs() }
        // 清理历史包，避免占空间
        dir.listFiles()?.forEach { if (it.name.endsWith(".apk")) it.delete() }
        val out = File(dir, "sap-${info.versionCode}.apk")

        // 经后端计量重定向端点下载：后端记一笔下载流量后 302 到 COS 直链，
        // 真实字节仍由 CDN 传输（不经自家服务器）；带 sap-token 以按会员归属流量。
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val meterUrl = base + "api/file/go?url=" +
            java.net.URLEncoder.encode(cosUrl, "UTF-8") +
            "&name=" + java.net.URLEncoder.encode("sap-${info.versionCode}.apk", "UTF-8")
        val reqBuilder = Request.Builder().url(meterUrl)
        tokenProvider()?.takeIf { it.isNotBlank() }?.let { reqBuilder.addHeader("sap-token", it) }
        val resp = http.newCall(reqBuilder.build()).execute()
        resp.use {
            if (!it.isSuccessful) error("下载失败：HTTP ${it.code}")
            val body = it.body ?: error("下载失败：服务器无响应")
            val total = if (info.size > 0) info.size else body.contentLength()
            val digest = MessageDigest.getInstance("SHA-256")
            body.byteStream().use { input ->
                FileOutputStream(out).use { fos ->
                    val buf = ByteArray(8192)
                    var read = 0L
                    while (true) {
                        coroutineContext.ensureActive() // 支持取消
                        val n = input.read(buf)
                        if (n < 0) break
                        fos.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        read += n
                        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            // 完整性校验：后端配了 sha256 才校验
            val expect = info.sha256?.trim()?.lowercase()
            if (!expect.isNullOrEmpty()) {
                val actual = digest.digest().joinToString("") { b -> "%02x".format(b) }
                if (actual != expect) {
                    out.delete()
                    error("安装包校验失败，可能被篡改或下载损坏，请重试")
                }
            }
        }
        onProgress(1f)
        out
    }
}
