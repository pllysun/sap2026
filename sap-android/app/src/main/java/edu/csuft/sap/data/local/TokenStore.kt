package edu.csuft.sap.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 登录凭证本地存储（加密）。
 * 用 EncryptedSharedPreferences（Android Keystore 派生密钥）保存长效 token，
 * 只要本地存在即可免密登录。
 */
class TokenStore(context: Context) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "sap_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_TOKEN) else putString(KEY_TOKEN, value)
            }.apply()
        }

    fun hasToken(): Boolean = !token.isNullOrBlank()

    /** 确认协议后的登录必须先落盘成功，才能跳转主页面。 */
    fun saveConfirmed(value: String) {
        require(value.isNotBlank())
        check(prefs.edit().putString(KEY_TOKEN, value).commit()) { "无法保存登录状态" }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_TOKEN = "sap_token"
    }
}
