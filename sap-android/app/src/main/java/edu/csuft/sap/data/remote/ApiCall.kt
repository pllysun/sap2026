package edu.csuft.sap.data.remote

import edu.csuft.sap.data.remote.dto.ApiResult
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.HttpException
import kotlinx.coroutines.CancellationException

private fun friendly(e: Throwable): String = when (e) {
    is SocketTimeoutException -> "连接超时，请稍后重试"
    is UnknownHostException, is ConnectException -> "无法连接服务器，请检查网络或后端地址"
    is HttpException -> if (e.code() == 428) "请完成教务短信验证" else "服务器请求失败（HTTP ${e.code()}）"
    is IOException -> "网络异常：${e.message ?: "请稍后重试"}"
    else -> e.message ?: "请求失败"
}

private fun errorCode(e: Throwable): Int = (e as? HttpException)?.code() ?: -1

/** 连接级失败（服务器不可达：超时/无网络/DNS）。IOException 类=连不上；HttpException(有HTTP响应)不算。 */
private fun isOffline(e: Throwable): Boolean = e is IOException

/** 调用返回数据的接口；code==200 且 data 非空视为成功。 */
suspend fun <T> apiData(block: suspend () -> ApiResult<T>): Outcome<T> = try {
    val r = block()
    val d = r.data
    if (r.code == 200 && d != null) Outcome.Success(d)
    else Outcome.Error(r.message ?: "请求失败", r.code)
} catch (e: Throwable) {
    if (e is CancellationException) throw e
    Outcome.Error(friendly(e), code = errorCode(e), offline = isOffline(e))
}

/** 调用仅关心成败的接口（绑定/解绑/登出等）。 */
suspend fun apiUnit(block: suspend () -> ApiResult<*>): Outcome<Unit> = try {
    val r = block()
    if (r.code == 200) Outcome.Success(Unit)
    else Outcome.Error(r.message ?: "请求失败", r.code)
} catch (e: Throwable) {
    if (e is CancellationException) throw e
    Outcome.Error(friendly(e), code = errorCode(e), offline = isOffline(e))
}
