package edu.csuft.sap.ui.auth

/** 验证结果仅暂存内存；确认才交给持久化操作，拒绝直接丢弃。 */
internal class LoginApproval<T : Any> {
    private var pending: T? = null
    fun stage(value: T) { pending = value }
    fun discard() { pending = null }
    fun approve(persist: (T) -> Unit): Boolean {
        val value = pending ?: return false
        persist(value)
        pending = null
        return true
    }
}
