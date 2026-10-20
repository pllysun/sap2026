package edu.csuft.sap.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** 侧载安装辅助：判断/申请“未知来源”权限 + 调起系统安装器。 */
object ApkInstaller {

    /** 校验包名、版本和签名，避免错误安装包进入系统安装流程。 */
    @Suppress("DEPRECATION")
    fun requireTrustedPackage(context: Context, file: File) {
        val flags = if (android.os.Build.VERSION.SDK_INT >= 28)
            android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES else android.content.pm.PackageManager.GET_SIGNATURES
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: error("无法读取安装包签名")
        val installed = pm.getPackageInfo(context.packageName, flags)
        require(archive.packageName == context.packageName) { "安装包不属于软协课表" }
        val archiveVersion = if (android.os.Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        val installedVersion = if (android.os.Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        require(archiveVersion > installedVersion) { "安装包版本不是更新版本" }
        fun signers(info: android.content.pm.PackageInfo): Set<String> {
            val certificates = if (android.os.Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return certificates.orEmpty().map { android.util.Base64.encodeToString(it.toByteArray(), android.util.Base64.NO_WRAP) }.toSet()
        }
        val trusted = signers(installed)
        require(trusted.isNotEmpty() && signers(archive) == trusted) { "安装包签名不匹配，请通过官方渠道下载" }
    }

    /** 是否已被授权安装未知来源应用（Android 8+ 按应用授权）。 */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** 跳转到系统“允许安装未知应用”授权页（仅针对本应用）。 */
    fun requestInstallPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** 用 FileProvider 生成 content:// URI 并调起系统安装器。 */
    fun install(context: Context, file: File) {
        requireTrustedPackage(context, file)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
