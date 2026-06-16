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
import edu.csuft.sap.di.Graph
import edu.csuft.sap.ui.icons.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PORTAL_URL = "https://webvpn.csuft.edu.cn"
private const val SSO_URL = "https://http-jwgl-csuft-edu-cn-80.webvpn.csuft.edu.cn/Logon.do?method=logonByZnlkd"
private const val SCHEDULE_URL = "https://http-jwgl-csuft-edu-cn-80.webvpn.csuft.edu.cn/jsxsd/xskb/xskb_list.do"
private const val JXZL_URL = "https://http-jwgl-csuft-edu-cn-80.webvpn.csuft.edu.cn/jsxsd/jxzl/jxzl_query"
private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

// 导入步骤：0 空闲 / 1 进教务SSO / 2 打开课表页 / 3 抽取课表 / 4 抓教学周历(开学日期)
private const val STEP_IDLE = 0
private const val STEP_SSO = 1
private const val STEP_SCHEDULE = 2
private const val STEP_EXTRACT = 3
private const val STEP_CALENDAR = 4

/**
 * WebVPN 课表导入（WakeUp 式）：内嵌 WebView 让用户登统一身份，点「导入」后自动进强智、打开课表页，
 * 注入 JS 取页面 HTML，端上解析成课表写入本地「WebVPN 课表」源。不走后端爬虫。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebImportScreen(onClose: () -> Unit, onImported: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("请登录学校统一身份，登录成功后点右上「导入课表」") }
    var busy by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(STEP_IDLE) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var importedTerm by remember { mutableStateOf<String?>(null) }
    var importedCount by remember { mutableIntStateOf(0) }
    var sawLogin by remember { mutableStateOf(false) }   // 见过登录页→之后跳到非登录页判定为“刚登录成功”
    var autoTried by remember { mutableStateOf(false) }  // 自动触发导入只做一次

    fun finishImport(term: String?, count: Int, start: String?) {
        step = STEP_IDLE
        busy = false
        status = "已导入「$term」共 $count 门课" + (start?.let { " · 开学日 $it" } ?: "")
        onImported()
    }

    fun handleCalendarHtml(html: String) {
        scope.launch {
            val term = importedTerm
            val start = withContext(Dispatchers.Default) { WebCalendarParser.parseSemesterStart(html, term) }
            if (term != null && start != null) {
                Graph.scheduleStore.setSemesterStart(AccountManager.WEBVIEW_ACCOUNT, term, start)
            }
            finishImport(term, importedCount, start)
        }
    }

    fun handleHtml(html: String) {
        scope.launch {
            val res = withContext(Dispatchers.Default) { WebScheduleParser.parse(html) }
            if (res?.term == null || res.courses.isEmpty()) {
                step = STEP_IDLE; busy = false
                status = "未抓到课表。请确认已登录教务系统后重试（若停在登录页，请先完成登录）"
                return@launch
            }
            Graph.scheduleStore.importWebview(
                AccountManager.WEBVIEW_ACCOUNT, res.term, res.courses, res.remarks,
            )
            Graph.accountManager.useWebview()
            CookieManager.getInstance().flush()
            importedTerm = res.term
            importedCount = res.courses.size
            // 课表已入；顺带抓教学周历拿开学日期(best-effort，失败不影响导入)
            status = "课表已导入，正在获取开学日期…"
            step = STEP_CALENDAR
            webView?.loadUrl("$JXZL_URL?xnxq01id=" + java.net.URLEncoder.encode(res.term, "UTF-8"))
        }
    }

    fun startImport() {
        val wv = webView ?: return
        autoTried = true
        busy = true
        step = STEP_SSO
        status = "正在进入教务系统…"
        wv.loadUrl(SSO_URL)
    }

    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onClose()
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
            if (busy) {
                CircularProgressIndicator(Modifier.size(22.dp).padding(end = 8.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = { startImport() }) { Text("导入课表") }
            }
        }

        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    // 仅 debug：软件渲染层，使 adb screencap 能截到 WebView 内容(便于自动化测试)；release 走硬件层
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
                    // 门户/登录页用默认(移动)UA 正常渲染；抓课表前再切桌面 UA 拿 #kbtable。
                    // 不开 useWideViewPort/loadWithOverviewMode，避免登录页按桌面宽度被缩放到几乎不可见(白屏)。
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            if (newProgress < 100 && step == STEP_IDLE) status = "页面加载中… $newProgress%"
                        }
                    }
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onHtml(html: String) {
                            // JS 线程回调 → 切回主线程处理
                            post { handleHtml(html) }
                        }

                        @JavascriptInterface
                        fun onCalendarHtml(html: String) {
                            post { handleCalendarHtml(html) }
                        }
                    }, "SapBridge")
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            val u = url ?: return
                            when (step) {
                                STEP_IDLE -> {
                                    // #3 登录重定向后自动导入：先见登录页(sawLogin)，登录成功跳到 webvpn 非登录页 → 自动开抓
                                    val lower = u.lowercase()
                                    val isLogin = lower.contains("login") || lower.contains("/cas") ||
                                        lower.contains("authentication") || lower.contains("logon") ||
                                        lower.contains("passport")
                                    if (isLogin) {
                                        sawLogin = true
                                    } else if (sawLogin && !autoTried && u.contains("webvpn.csuft.edu.cn")) {
                                        startImport()
                                    }
                                }
                                STEP_SSO -> when {
                                    u.contains("/jsxsd/") -> {
                                        step = STEP_SCHEDULE
                                        view.settings.userAgentString = DESKTOP_UA // 抓课表用桌面 UA → 强智返回 #kbtable
                                        view.loadUrl(SCHEDULE_URL)
                                    }
                                    u.contains("/cas/login") || u.contains("authentication") || u.contains("/login") -> {
                                        step = STEP_IDLE; busy = false; autoTried = false // 未登录→重置，允许登录后再次自动触发
                                        status = "请先在页面登录学校统一身份，再点「导入课表」"
                                    }
                                }
                                STEP_SCHEDULE -> if (u.contains("xskb_list.do")) {
                                    step = STEP_EXTRACT
                                    status = "正在抓取课表…"
                                    view.evaluateJavascript(
                                        "(function(){try{SapBridge.onHtml(document.documentElement.outerHTML);}catch(e){}})();",
                                        null,
                                    )
                                }
                                STEP_CALENDAR -> if (u.contains("jxzl_query")) {
                                    view.evaluateJavascript(
                                        "(function(){try{SapBridge.onCalendarHtml(document.documentElement.outerHTML);}catch(e){}})();",
                                        null,
                                    )
                                } else {
                                    finishImport(importedTerm, importedCount, null) // 周历页未正常打开→不强求开学日期，收尾
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
