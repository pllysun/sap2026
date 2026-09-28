package edu.csuft.sap.ui.profile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 保留最后一次成功的头像；只有新图加载成功且内容不同才更新显示。由主线程调用。 */
internal class RetainedAvatar<T>(private val sameContent: (T, T) -> Boolean) {
    private val _image = MutableStateFlow<T?>(null)
    val image: StateFlow<T?> = _image.asStateFlow()
    private var owner: String? = null
    private var loadedKey: String? = null
    private var pendingKey: String? = null
    private var targetKey: String? = null
    private var generation = 0L

    fun activate(account: String) {
        if (owner == account) return
        owner = account
        reset()
    }

    private fun reset() {
        generation++
        loadedKey = null
        pendingKey = null
        targetKey = null
        _image.value = null
    }

    suspend fun load(account: String, key: String?, fetch: suspend () -> T?) {
        activate(account)
        if (key == null) {
            reset()
            return
        }
        if (pendingKey == key) return
        targetKey = key
        // 即使回到已加载的旧地址，也要让正在下载的其它版本失效。
        val request = ++generation
        pendingKey = null
        if (loadedKey == key) return
        pendingKey = key
        try {
            val next = fetch() ?: return
            if (request != generation) {
                // 冷启动时，旧缓存解码可能晚于新资料返回；新图未就绪时仍可先展示旧图。
                if (owner == account && targetKey != null && _image.value == null) _image.value = next
                return
            }
            val previous = _image.value
            if (previous == null || !sameContent(previous, next)) _image.value = next
            loadedKey = key
        } finally {
            if (request == generation) pendingKey = null
        }
    }
}
