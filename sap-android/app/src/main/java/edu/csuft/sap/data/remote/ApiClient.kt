package edu.csuft.sap.data.remote

import com.google.gson.JsonParser
import edu.csuft.sap.data.account.JwMfaState
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private const val JW_MFA_CODE = 428
private const val MFA_RESPONSE_PEEK_BYTES = 64L * 1024L

/**
 * 识别教务短信验证挑战，并把旧后端的「HTTP 200 + 业务码 428」规范成 HTTP 428。
 *
 * Retrofit 会先按接口声明的数据类型反序列化 2xx 响应，再由业务层检查 code。成绩等接口的 data
 * 是数组，而 MFA 的 data 是挑战对象；如果仍保留 HTTP 200，Gson 会先抛出 Expected BEGIN_ARRAY，
 * 导致全局验证码弹框还未来得及接管。改为非 2xx 后 Retrofit 不再用成功模型解析 data。
 */
internal fun normalizeJwMfaResponse(
    response: Response,
    onChallenge: (challengeId: String, phone: String) -> Unit,
): Response {
    val isMfa = runCatching {
        val root = JsonParser.parseString(response.peekBody(MFA_RESPONSE_PEEK_BYTES).string())
        if (!root.isJsonObject) return@runCatching false
        val obj = root.asJsonObject
        if (obj.get("code")?.asInt != JW_MFA_CODE) return@runCatching false

        val dataElement = obj.get("data")
        val data = if (dataElement != null && dataElement.isJsonObject) dataElement.asJsonObject else null
        val challengeId = data?.get("challengeId")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
        val phone = data?.get("phone")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
        onChallenge(challengeId, phone)
        true
    }.getOrDefault(false)

    return if (isMfa && response.isSuccessful) {
        response.newBuilder()
            .code(JW_MFA_CODE)
            .message("Precondition Required")
            .build()
    } else {
        response
    }
}

object ApiClient {

    /**
     * @param baseUrl  形如 http://10.0.2.2:8081（自动补尾斜杠）
     * @param tokenProvider 每次请求时读取本地 token，注入 sap-token 头
     * @param connectTimeoutSec 连接超时（秒）。普通接口 10s；连通性探针用 3s 以便快速判离线。
     * @param readTimeoutSec 读超时（秒）。课表/成绩等后端代抓耗时长，默认放宽 60s；探针用 3s。
     */
    fun create(
        baseUrl: String,
        tokenProvider: () -> String?,
        connectTimeoutSec: Long = 10,
        readTimeoutSec: Long = 60,
    ): ApiService {
        val authInterceptor = Interceptor { chain ->
            val builder = chain.request().newBuilder().header("X-SAP-Client", "app")
            tokenProvider()?.takeIf { it.isNotBlank() }?.let {
                builder.addHeader("sap-token", it)
            }
            chain.proceed(builder.build())
        }
        // 全局拦截教务短信二次验证(MFA)：先触发短信输入框，再阻止 Retrofit 将挑战对象误解析成业务数组。
        val mfaInterceptor = Interceptor { chain ->
            normalizeJwMfaResponse(chain.proceed(chain.request()), JwMfaState::require)
        }
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(mfaInterceptor)
            .addInterceptor(logging)
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
