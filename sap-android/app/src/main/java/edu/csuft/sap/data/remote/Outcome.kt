package edu.csuft.sap.data.remote

/** 简单的结果包装：成功带数据，失败带消息。 */
sealed interface Outcome<out T> {
    data class Success<T>(val data: T) : Outcome<T>

    /**
     * @param code 后端返回的业务码（如 401）；异常时为 -1。
     * @param offline 是否「连不上服务器」（连接级失败：超时/无网络/DNS 失败）。
     *   用于离线兜底判断——offline=true 表示后端不可达（宕机/没网），**绝不能据此清登录态**；
     *   offline=false 即便是错误也代表服务器有响应（可达）。
     */
    data class Error(val message: String, val code: Int = -1, val offline: Boolean = false) : Outcome<Nothing>
}
