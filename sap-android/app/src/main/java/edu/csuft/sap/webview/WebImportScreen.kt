package edu.csuft.sap.webview

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import edu.csuft.sap.data.account.AccountManager
import edu.csuft.sap.data.account.MemberState
import edu.csuft.sap.data.schedule.TermScan
import edu.csuft.sap.data.schedule.TermUtil
import edu.csuft.sap.di.Graph
import edu.csuft.sap.ui.icons.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val PORTAL_URL = "https://webvpn.csuft.edu.cn"
private const val SSO_URL = "https://http-jwgl-csuft-edu-cn-80.webvpn.csuft.edu.cn/Logon.do?method=logonByZnlkd"
private const val SCHEDULE_URL = "https://http-jwgl-csuft-edu-cn-80.webvpn.csuft.edu.cn/jsxsd/xskb/xskb_list.do"
private const val JXZL_URL = "https://http-jwgl-csuft-edu-cn-80.webvpn.csuft.edu.cn/jsxsd/jxzl/jxzl_query"
private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

// 取页面 outerHTML 经 SapBridge 回传（大字符串走 JS 接口，不走 evaluateJavascript 的 JSON 编码）
private const val JS_GRAB_HTML =
    "(function(){try{SapBridge.onHtml(document.documentElement.outerHTML);}catch(e){SapBridge.onHtml('');}})();"

private fun isLoginUrl(u: String): Boolean {
    val l = u.lowercase()
    return l.contains("login") || l.contains("/cas") || l.contains("authentication") ||
        l.contains("logon") || l.contains("passport")
}

private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

/** WebView 事件等待器：把 onPageFinished / SapBridge.onHtml 桥接给协程顺序驱动。 */
private class WebWaiters {
    var onPage: ((String) -> Unit)? = null
    var onHtml: ((String) -> Unit)? = null
}

/**
 * WebVPN 课表导入（WakeUp 式）：内嵌 WebView 登统一身份后端上抓课表，不走后端爬虫。
 * 用「协程顺序驱动」（加载→等页面→抓HTML→解析），避免回调状态机的并发竞态。
 * 登录检测：用户在内嵌 WebView 登录成功后，自动跳到课表页：
 * - 会员：**全自动**枚举所有学期，从最早课表起必扫 8 个学期(覆盖四年)、之后有课继续到无课为止，升序合并保存。
 * - 非会员：自动停在课表页，由用户**手动点「导入课表」**抓当前显示学期(可先用页面学期下拉切换)，单课表覆盖。
 * 离开本页即清除网页登录态(Cookie/缓存)，下次进入需重新登录——便于换账号、避免会话长期不过期。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebImportScreen(onClose: () -> Unit, onImported: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isMember = MemberState.isMember
    val waiters = remember { WebWaiters() }
    var status by remember {
        mutableStateOf(
            if (isMember) "登录学校统一身份后，点右上「导入课表」自动抓取所有学期"
            else "登录并进入课表页后，可切到目标学期再点「导入课表」",
        )
    }
    var busy by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var sawLogin by remember { mutableStateOf(false) }    // 见过登录页 → 之后回到非登录页判定为刚登录成功
    var autoStarted by remember { mutableStateOf(false) }  // 登录后自动流程只触发一次
    var importJob by remember { mutableStateOf<Job?>(null) }

    // ---- 协程驱动用的挂起原语 ----
    suspend fun awaitUrl(timeoutMs: Long, match: (String) -> Boolean): String? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                waiters.onPage = { u -> if (match(u)) { waiters.onPage = null; if (cont.isActive) cont.resume(u) } }
                cont.invokeOnCancellation { waiters.onPage = null }
            }
        }

    suspend fun grabHtml(timeoutMs: Long): String? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                waiters.onHtml = { h -> waiters.onHtml = null; if (cont.isActive) cont.resume(h) }
                cont.invokeOnCancellation { waiters.onHtml = null }
                val wv = webView
                if (wv == null) { waiters.onHtml = null; if (cont.isActive) cont.resume("") }
                else wv.evaluateJavascript(JS_GRAB_HTML, null)
            }
        }

    fun stop(msg: String) { busy = false; status = msg }

    // 登录后进 jsxsd 并打开课表页（桌面 UA）。成功返回 true；未登录/超时 false 并已置 status。
    suspend fun gotoSchedulePage(wv: WebView): Boolean {
        status = "正在进入教务系统…"
        wv.loadUrl(SSO_URL)
        val landed = awaitUrl(30000) { it.contains("/jsxsd/") || isLoginUrl(it) }
        if (landed == null) { stop("进入教务超时，请重试"); return false }
        if (isLoginUrl(landed) || !landed.contains("/jsxsd/")) {
            stop("请先在页面登录学校统一身份，再点「导入课表」"); return false
        }
        wv.settings.userAgentString = DESKTOP_UA // 桌面 UA → 强智返回 #kbtable
        wv.loadUrl(SCHEDULE_URL)
        if (awaitUrl(30000) { it.contains("xskb_list.do") } == null) { stop("打开课表页超时，请重试"); return false }
        return true
    }

    suspend fun fetchTermStart(wv: WebView, term: String): String? {
        wv.loadUrl("$JXZL_URL?xnxq01id=" + enc(term))
        if (awaitUrl(15000) { it.contains("jxzl_query") } == null) return null
        val cal = grabHtml(10000) ?: return null
        return withContext(Dispatchers.Default) { WebCalendarParser.parseSemesterStart(cal, term) }
    }

    fun startMemberImport() {
        val wv = webView ?: return
        busy = true
        importJob = scope.launch {
            // 进课表页拿“当前学期”作为锚点
            if (!(wv.url ?: "").contains("xskb_list.do")) {
                if (!gotoSchedulePage(wv)) return@launch
            } else {
                wv.settings.userAgentString = DESKTOP_UA
            }
            status = "正在识别当前学期…"
            val curHtml = grabHtml(15000)
            val curRes = if (curHtml != null) withContext(Dispatchers.Default) { WebScheduleParser.parse(curHtml) } else null
            val current = curRes?.term?.trim()
            if (current == null || !TermUtil.isTerm(current)) { stop("未能识别当前学期，请重试"); return@launch }
            var cCount = 0
            // 与教务一致：以当前学期为锚，向过去/将来扫描，连续 2 个空学期止
            val withData = TermScan.scanAround(current) { term ->
                status = "正在扫描 $term…"
                val res = if (term == current) curRes else {
                    wv.settings.userAgentString = DESKTOP_UA
                    wv.loadUrl("$SCHEDULE_URL?xnxq01id=" + enc(term))
                    if (awaitUrl(20000) { it.contains("xskb_list.do") } != null) {
                        val h = grabHtml(15000)
                        if (h != null) withContext(Dispatchers.Default) { WebScheduleParser.parse(h) } else null
                    } else null
                }
                // 仅当页面显示的就是所请求学期且非空才算有课（防强智把无效学期回显成当前学期）
                val ok = res?.term?.trim() == term && res.courses.isNotEmpty()
                if (ok && res != null) {
                    Graph.scheduleStore.importWebview(
                        AccountManager.WEBVIEW_ACCOUNT, term, res.courses, res.remarks, replaceAll = false,
                    )
                    cCount += res.courses.size
                    fetchTermStart(wv, term)?.let { Graph.scheduleStore.setSemesterStart(AccountManager.WEBVIEW_ACCOUNT, term, it) }
                }
                ok
            }
            // 当前学期即使空也建一份（如大四下），保持学期完整、与教务一致
            if (current !in withData) {
                curRes?.let {
                    Graph.scheduleStore.importWebview(
                        AccountManager.WEBVIEW_ACCOUNT, current, it.courses, it.remarks, replaceAll = false,
                    )
                }
            }
            Graph.accountManager.useWebview()
            // 默认选中：按日期选当前/下一学期；当前无课则最近有课学期；都没有则当前
            val month = java.time.LocalDate.now().monthValue
            val def = TermScan.defaultTerm(current, withData, month) ?: current
            Graph.scheduleStore.setActiveProfile(AccountManager.WEBVIEW_ACCOUNT, "webview:$def")
            CookieManager.getInstance().flush()
            if (withData.isNotEmpty()) {
                stop("已导入 ${withData.size} 个学期、共 $cCount 门课")
                onImported()
            } else {
                stop("当前学期暂无课表数据，可「换账号」或稍后重试")
            }
        }
    }

    fun startNonMemberImport() {
        val wv = webView ?: return
        busy = true
        importJob = scope.launch {
            // 不在课表页：先进课表页让用户选学期，不自动抓
            if (!(wv.url ?: "").contains("xskb_list.do")) {
                if (!gotoSchedulePage(wv)) return@launch
                stop("已进入课表页。可用页面顶部「学年学期」下拉切到目标学期，再点「导入课表」")
                return@launch
            }
            // 已在课表页：抓当前显示学期，单课表覆盖
            wv.settings.userAgentString = DESKTOP_UA
            status = "正在抓取当前学期课表…"
            val html = grabHtml(15000)
            val res = if (html != null) withContext(Dispatchers.Default) { WebScheduleParser.parse(html) } else null
            if (res?.term == null || res.courses.isEmpty()) {
                stop("未抓到课表，请确认在课表页（必要时用学期下拉切到该学期）后重试"); return@launch
            }
            Graph.scheduleStore.importWebview(
                AccountManager.WEBVIEW_ACCOUNT, res.term, res.courses, res.remarks, replaceAll = true,
            )
            Graph.accountManager.useWebview()
            status = "课表已导入，正在获取开学日期…"
            val start = fetchTermStart(wv, res.term)
            if (start != null) Graph.scheduleStore.setSemesterStart(AccountManager.WEBVIEW_ACCOUNT, res.term, start)
            CookieManager.getInstance().flush()
            stop("已导入「${res.term}」共 ${res.courses.size} 门课" + (start?.let { " · 开学日 $it" } ?: ""))
            onImported()
        }
    }

    fun onImport() {
        if (busy) return
        if (isMember) startMemberImport() else startNonMemberImport()
    }

    // 换账号：清网页登录态回门户重登（解决“一打开就是上次登录”、便于切教务账号）
    fun resetSession() {
        importJob?.cancel()
        val wv = webView ?: return
        busy = false; autoStarted = false; sawLogin = false
        status = "已清除网页登录，请重新登录"
        val cm = CookieManager.getInstance()
        cm.removeAllCookies(null)
        cm.removeSessionCookies(null)
        cm.flush()
        android.webkit.WebStorage.getInstance().deleteAllData()
        wv.clearCache(true)
        wv.clearHistory()
        wv.settings.userAgentString = null // 复位默认(移动)UA，登录页正常渲染
        wv.loadUrl(PORTAL_URL)
    }

    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onClose()
    }

    // 离开本页即清网页登录态：下次进入需重新登录（便于换账号、避免会话长期不过期）
    DisposableEffect(Unit) {
        onDispose {
            importJob?.cancel()
            runCatching {
                val cm = CookieManager.getInstance()
                cm.removeAllCookies(null)
                cm.flush()
                android.webkit.WebStorage.getInstance().deleteAllData()
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(top = 0.dp)) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(AppIcons.Back, "返回") }
            Column(Modifier.weight(1f)) {
                Text("WebVPN 导入课表", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(status, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            TextButton(onClick = { resetSession() }) { Text("换账号", fontSize = 13.sp) }
            if (busy) {
                CircularProgressIndicator(Modifier.size(22.dp).padding(end = 8.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = { onImport() }) { Text("导入课表") }
            }
        }

        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            onRelease = { it.destroy() },
            factory = { ctx ->
                WebView(ctx).apply {
                    // 仅 debug：软件渲染层，使 adb screencap 能截到 WebView 内容；release 走硬件层
                    if (edu.csuft.sap.BuildConfig.DEBUG) {
                        setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            if (newProgress < 100 && !busy) status = "页面加载中… $newProgress%"
                        }
                    }
                    addJavascriptInterface(object {
                        @JavascriptInterface fun onHtml(html: String) { post { waiters.onHtml?.invoke(html) } }
                    }, "SapBridge")
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            val u = url ?: ""
                            waiters.onPage?.invoke(u)
                            // 空闲态自动流程：先见登录页 → 回到非登录的 webvpn/jsxsd 页 = 刚登录成功 → 自动开始
                            if (!busy && !autoStarted) {
                                if (isLoginUrl(u)) {
                                    sawLogin = true
                                } else if (sawLogin && (u.contains("webvpn.csuft.edu.cn") || u.contains("/jsxsd/"))) {
                                    autoStarted = true
                                    view.post { onImport() }
                                }
                            }
                        }
                    }
                    loadUrl(PORTAL_URL)
                    webView = this
                }
            },
        )
    }
}
