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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val WEBVPN_HOME_URL = "https://webvpn.csuft.edu.cn/site-nav/home"
private const val WEBVPN_PORTAL_URL =
    "https://https-portal-csuft-edu-cn-443.webvpn.csuft.edu.cn/main.html#/Index"
private const val WEBVPN_JWXT_BASE = "https://http-jwxt-csuft-edu-cn-80.webvpn.csuft.edu.cn"
private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

/** 新版强智课表必须带 viweType=0 才返回实际 qz-weeklyTable，而不是外层空壳页。 */
private fun scheduleUrl(base: String, term: String? = null): String = buildString {
    append("$base/jsxsd/xskb/xskb_list.do?viweType=0")
    if (!term.isNullOrBlank()) append("&xnxq01id=").append(enc(term))
}
private fun calendarUrl(base: String) = "$base/jsxsd/jxzl/jxzl_query"
/** 仅把强智的具体课表路由视作可导入页，避免 xsMain/课程列表等页面被误判。 */
internal fun isScheduleUrl(url: String?): Boolean {
    val uri = runCatching { java.net.URI.create(url) }.getOrNull() ?: return false
    return uri.host.equals("http-jwxt-csuft-edu-cn-80.webvpn.csuft.edu.cn", ignoreCase = true) &&
        uri.path.orEmpty().lowercase().removeSuffix("/") == "/jsxsd/xskb/xskb_list.do"
}

// 取页面 outerHTML 经 SapBridge 回传（大字符串走 JS 接口，不走 evaluateJavascript 的 JSON 编码）
private const val JS_GRAB_HTML =
    "(function(){try{var h=document.documentElement.outerHTML;var f=document.getElementById('Iframe1');if(f&&f.contentDocument&&f.contentDocument.documentElement&&f.contentDocument.documentElement.outerHTML.length>h.length/2){h=f.contentDocument.documentElement.outerHTML;}SapBridge.onHtml(h);}catch(e){SapBridge.onHtml(document.documentElement.outerHTML||'');}})();"

/**
 * 融合门户是单页应用，教务入口的票据由“教务系统”服务卡片点击时生成。
 * 不能只加载最终 xsMainV.htmlx，否则强智会返回“请先登录系统”。
 * 通过 DOM 点击真实服务卡片，保留门户现有的登录态和跳转参数。
 */
private val JS_CLICK_JW_SERVICE = """
(function(){
  try {
    var norm=function(s){return (s||'').replace(/\\s+/g,'').trim();};
    var selectors='a,button,[role="button"],[class*="service"],[class*="Service"],[class*="card"],[class*="Card"]';
    var nodes=Array.prototype.slice.call(document.querySelectorAll(selectors));
    var hit=nodes.find(function(n){return norm(n.innerText||n.textContent).indexOf('教务系统')>=0;});
    if(!hit) return 'not-found';
    var target=hit.closest('a,button,[role="button"]')||hit;
    target.click();
    return 'clicked';
  } catch(e) { return 'error'; }
})();
""".trimIndent()

internal fun isWebImportLoginUrl(u: String): Boolean {
    val uri = runCatching { java.net.URI.create(u) }.getOrNull() ?: return false
    val host = uri.host.orEmpty()
    return (host.equals("cas.csuft.edu.cn", ignoreCase = true) ||
        host.equals("https-cas-csuft-edu-cn-443.webvpn.csuft.edu.cn", ignoreCase = true)) &&
        (uri.path ?: "").startsWith("/cas")
}

internal fun isWebvpnHomeUrl(u: String): Boolean {
    val uri = runCatching { java.net.URI.create(u) }.getOrNull() ?: return false
    return uri.host.equals("webvpn.csuft.edu.cn", ignoreCase = true) &&
        (uri.path ?: "").startsWith("/site-nav")
}

internal fun isWebvpnPortalUrl(u: String): Boolean {
    val uri = runCatching { java.net.URI.create(u) }.getOrNull() ?: return false
    return uri.host.equals("https-portal-csuft-edu-cn-443.webvpn.csuft.edu.cn", ignoreCase = true) &&
        (uri.path ?: "").equals("/main.html", ignoreCase = true)
}

internal fun isJwFrameworkUrl(u: String): Boolean {
    val uri = runCatching { java.net.URI.create(u) }.getOrNull() ?: return false
    return uri.host.equals("http-jwxt-csuft-edu-cn-80.webvpn.csuft.edu.cn", ignoreCase = true) &&
        uri.path.orEmpty().lowercase().removeSuffix("/") == "/jsxsd/framework/xsmainv.htmlx"
}

private fun isServiceUnavailableHtml(html: String?): Boolean {
    val text = html.orEmpty().lowercase()
    return text.contains("503 service unavailable") ||
        text.contains("no server is available to handle this request")
}

internal fun isUnexpectedWebvpnProxyLanding(u: String): Boolean {
    val uri = runCatching { java.net.URI.create(u) }.getOrNull() ?: return false
    val host = uri.host.orEmpty().lowercase()
    return host.endsWith(".webvpn.csuft.edu.cn") &&
        host != "https-cas-csuft-edu-cn-443.webvpn.csuft.edu.cn" &&
        host != "http-jwxt-csuft-edu-cn-80.webvpn.csuft.edu.cn"
}

private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

/** WebView 事件等待器：把 onPageFinished / SapBridge.onHtml 桥接给协程顺序驱动。 */
private class WebWaiters {
    var onPage: ((String) -> Unit)? = null
    var onHtml: ((String) -> Unit)? = null
}

/**
 * WebVPN 课表导入（WakeUp 式）：内嵌 WebView 登录统一身份后端上抓课表，不走后端爬虫。
 * 用「协程顺序驱动」（加载→等页面→抓HTML→解析），避免回调状态机的并发竞态。
 * 登录检测：用户在内嵌 WebView 登录成功后，自动跳到课表页：
 * - 完整导入：自动枚举学期并合并保存。
 * - 单槽导入：停在课表页，由用户选择学期后替换当前课表；不会删除其它历史课表。
 * 离开本页即清除网页登录态(Cookie/缓存)，下次进入需重新登录——便于换账号、避免会话长期不过期。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebImportScreen(onClose: () -> Unit, onImported: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fullFeatures = MemberState.hasFullAppFeatures
    val waiters = remember { WebWaiters() }
    var status by remember {
        mutableStateOf(
            if (fullFeatures) "登录学校统一身份后，点右上「导入课表」自动抓取所有学期"
            else "登录并进入课表页后，可切到目标学期再点「导入课表」",
        )
    }
    var busy by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var sawLogin by remember { mutableStateOf(false) }    // 见过登录页 → 之后回到非登录页判定为刚登录成功
    var autoStarted by remember { mutableStateOf(false) }  // 登录后自动流程只触发一次
    var redirectRecovered by remember { mutableStateOf(false) } // 回调误落其它代理系统时只纠正一次
    var importJob by remember { mutableStateOf<Job?>(null) }
    var activeJwBase by remember { mutableStateOf(WEBVPN_JWXT_BASE) }

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

    suspend fun clickPortalJwService(wv: WebView): Boolean {
        repeat(30) {
            val clicked = withTimeoutOrNull(1500) {
                suspendCancellableCoroutine { cont ->
                    wv.evaluateJavascript(JS_CLICK_JW_SERVICE) { raw ->
                        if (cont.isActive) cont.resume(raw.orEmpty().contains("clicked"))
                    }
                    cont.invokeOnCancellation { }
                }
            } == true
            if (clicked) return true
            delay(500)
        }
        return false
    }

    fun stop(msg: String) { busy = false; status = msg }

    // 登录后直接定位到强智的具体课表页（桌面 UA）。当前学校必须先经过
    // WebVPN 融合门户，再通过门户服务卡片进入 jwxt 的新版 xsMainV.htmlx。
    // 只接受具体课表路由并通过解析器确认页面，避免把 503/登录页误当成课表页。
    suspend fun gotoSchedulePage(wv: WebView): Boolean {
        suspend fun trySchedule(base: String): Boolean {
            wv.settings.userAgentString = DESKTOP_UA
            wv.loadUrl(scheduleUrl(base))
            if (awaitUrl(30000) { isScheduleUrl(it) } != null) {
                val html = grabHtml(15000)
                if (!isServiceUnavailableHtml(html) &&
                    withContext(Dispatchers.Default) { WebScheduleParser.parse(html) } != null
                ) {
                    activeJwBase = base
                    return true
                }
            }
            return false
            }

        // 如果页面还不是新版框架，先打开融合门户，再点击“教务系统”服务卡片。
        // 该点击会生成强智 SSO 所需的一次性票据；直接拼接最终 URL 会被判定为未登录。
        if (!isJwFrameworkUrl(wv.url.orEmpty())) {
            status = "正在打开融合门户…"
            wv.loadUrl(WEBVPN_PORTAL_URL)
            val portal = awaitUrl(20000) {
                isWebvpnPortalUrl(it) || isJwFrameworkUrl(it) || isWebImportLoginUrl(it)
            }
            if (portal != null && !isWebImportLoginUrl(portal)) {
                status = "正在进入新版教务系统…"
                if (!isJwFrameworkUrl(portal) && !clickPortalJwService(wv)) {
                    stop("未找到新版教务入口，请稍后重试")
                    return false
                }
                if (!isJwFrameworkUrl(wv.url.orEmpty())) {
                    awaitUrl(30000) { isJwFrameworkUrl(it) || isWebImportLoginUrl(it) }
                }
            }
        }

        // 新版入口是唯一主路径；失败即停止，不再跳转旧版教务。
        if (trySchedule(WEBVPN_JWXT_BASE)) return true
        stop("新版教务入口暂时无法打开，请稍后重试")
        return false
    }

    suspend fun fetchTermStart(wv: WebView, term: String): String? {
        // 先使用全校共享校历，缺失或服务器不可达才从当前已登录的学校网页读取。
        Graph.academicCalendarRepository.dates()[term]?.let { return it }
        wv.loadUrl("${calendarUrl(activeJwBase)}?xnxq01id=" + enc(term))
        if (awaitUrl(15000) { it.contains("jxzl_query") } == null) return null
        val cal = grabHtml(10000) ?: return null
        return withContext(Dispatchers.Default) { WebCalendarParser.parseSemesterStart(cal, term) }
    }

    fun startFullImport() {
        val wv = webView ?: return
        busy = true
        importJob = scope.launch {
            // 进课表页拿“当前学期”作为锚点
            if (!isScheduleUrl(wv.url)) {
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
                    wv.loadUrl(scheduleUrl(activeJwBase, term))
                    if (awaitUrl(20000) { isScheduleUrl(it) } != null) {
                        val h = grabHtml(15000)
                        if (h != null) withContext(Dispatchers.Default) { WebScheduleParser.parse(h) } else null
                    } else null
                }
                // 仅当页面显示的就是所请求学期且非空才算有课（防强智把无效学期回显成当前学期）
                if (res != null && res.term?.trim() == term && res.courses.isNotEmpty()) {
                    Graph.scheduleStore.importWebview(
                        AccountManager.WEBVIEW_ACCOUNT, term, res.courses, res.remarks, replaceAll = false,
                    )
                    cCount += res.courses.size
                    fetchTermStart(wv, term)?.let { Graph.scheduleStore.setSemesterStart(AccountManager.WEBVIEW_ACCOUNT, term, it) }
                    true
                } else {
                    false
                }
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
            Graph.scheduleStore.finishScheduleSync(AccountManager.WEBVIEW_ACCOUNT, (withData + current).toSet())
            CookieManager.getInstance().flush()
            if (withData.isNotEmpty()) {
                stop("已导入 ${withData.size} 个学期、共 $cCount 门课")
                onImported()
            } else {
                stop("当前学期暂无课表数据，可「换账号」或稍后重试")
            }
        }
    }

    fun startSingleSlotImport() {
        val wv = webView ?: return
        busy = true
        importJob = scope.launch {
            // 不在课表页：先进课表页让用户选学期，不自动抓
            if (!isScheduleUrl(wv.url)) {
                if (!gotoSchedulePage(wv)) return@launch
                stop("已进入课表页。可用页面顶部「学年学期」下拉切到目标学期，再点「导入课表」")
                return@launch
            }
            // 已在课表页：抓当前显示学期；已有内容时替换当前课表，没有时创建第一份。
            wv.settings.userAgentString = DESKTOP_UA
            status = "正在抓取当前学期课表…"
            val html = grabHtml(15000)
            val res = if (html != null) withContext(Dispatchers.Default) { WebScheduleParser.parse(html) } else null
            if (res?.term == null || res.courses.isEmpty()) {
                stop("未抓到课表，请确认在课表页（必要时用学期下拉切到该学期）后重试"); return@launch
            }
            Graph.scheduleStore.importWebviewSingleSlot(
                AccountManager.WEBVIEW_ACCOUNT, res.term, res.courses, res.remarks,
            )
            Graph.accountManager.useWebview()
            status = "课表已导入，正在获取开学日期…"
            Graph.scheduleStore.finishScheduleSync(AccountManager.WEBVIEW_ACCOUNT, setOf(res.term))
            val start = fetchTermStart(wv, res.term)
            if (start != null) Graph.scheduleStore.setSemesterStart(AccountManager.WEBVIEW_ACCOUNT, res.term, start)
            CookieManager.getInstance().flush()
            stop("已导入「${res.term}」共 ${res.courses.size} 门课" + (start?.let { " · 开学日 $it" } ?: ""))
            onImported()
        }
    }

    fun onImport() {
        if (busy) return
        if (fullFeatures) startFullImport() else startSingleSlotImport()
    }

    // 换账号：清网页登录态回门户重登（解决“一打开就是上次登录”、便于切教务账号）
    fun resetSession() {
        importJob?.cancel()
        val wv = webView ?: return
        busy = false; autoStarted = false; sawLogin = false; redirectRecovered = false
        status = "已清除网页登录，请重新登录"
        val cm = CookieManager.getInstance()
        cm.removeAllCookies(null)
        cm.removeSessionCookies(null)
        cm.flush()
        android.webkit.WebStorage.getInstance().deleteAllData()
        wv.clearCache(true)
        wv.clearHistory()
        wv.settings.userAgentString = null // 复位默认(移动)UA，登录页正常渲染
        wv.loadUrl(WEBVPN_HOME_URL)
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
                    settings.setSupportMultipleWindows(false)
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
                            // 先见 WebVPN CAS 登录页；回到明确的导航首页后再进强智，避免根路径/旧 returnUrl
                            // 把回调误送到教学质量系统登录页。门户卡片的 /login_znly 不作为 SSO 入口。
                            if (!busy && !autoStarted) {
                                if (isWebImportLoginUrl(u)) {
                                    sawLogin = true
                                } else if (sawLogin && (isWebvpnHomeUrl(u) || isWebvpnPortalUrl(u) || u.contains("/jsxsd/"))) {
                                    autoStarted = true
                                    view.post { onImport() }
                                } else if (sawLogin && !redirectRecovered && isUnexpectedWebvpnProxyLanding(u)) {
                                    redirectRecovered = true
                                    status = "登录成功，正在纠正 WebVPN 跳转…"
                                    view.post { view.loadUrl(WEBVPN_HOME_URL) }
                                }
                            }
                        }
                    }
                    loadUrl(WEBVPN_HOME_URL)
                    webView = this
                }
            },
        )
    }
}
