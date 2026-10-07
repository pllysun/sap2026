package com.sap.service;

import com.sap.common.BusinessException;
import com.sap.mapper.UserMapper;
import com.sap.service.mail.MailQueue;
import com.sap.util.IpUtil;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** The web and old Android updater share one authenticated download gate. */
@Service
public class AppDownloadService {
    private final AppDownloadStore store;
    private final AppVersionService versions;
    private final CosService cos;
    private final UserMapper users;
    private final MailQueue mail;
    private final TrafficService traffic;
    private final Semaphore transfers = new Semaphore(2);
    private final ExecutorService warmer = Executors.newSingleThreadExecutor(r -> { var t = new Thread(r, "app-download-cache"); t.setDaemon(true); return t; });
    private volatile Package ready;
    private volatile boolean warming;
    private final Path cache;
    public AppDownloadService(AppDownloadStore store, AppVersionService versions, CosService cos, UserMapper users, MailQueue mail, TrafficService traffic,
            @Value("${app.download.cache-dir:${file.upload.path:./uploads}/../data/app-downloads}") String directory) {
        this.store = store; this.versions = versions; this.cos = cos; this.users = users; this.mail = mail; this.traffic = traffic;
        cache = Path.of(directory).toAbsolutePath().normalize();
    }
    @PreDestroy public void stop() { warmer.shutdownNow(); }
    @EventListener(ApplicationReadyEvent.class) public void start() { refresh(); }
    @Scheduled(fixedDelay = 60_000) public synchronized void refresh() {
        if (warming || warmer.isShutdown()) return;
        var current = versions.getPublished();
        if (current.getVersionCode() <= 0 || ready != null && ready.sha.equals(current.getSha256()) && ready.version == current.getVersionCode()) return;
        warming = true;
        warmer.execute(() -> {
            try { ready = prepare(current); }
            catch (Exception e) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("App 安装包准备失败 ({})，稍后重试", e.getClass().getSimpleName()); }
            finally { warming = false; }
        });
    }
    private Package prepare(com.sap.vo.AppVersionVO version) throws Exception {
        String sha = version.getSha256();
        if (sha == null || !sha.matches("[a-f0-9]{64}") || version.getSize() <= 0 || version.getSize() > 100L * 1024 * 1024)
            throw new BusinessException(503, "安装包校验信息无效");
        String key = cos.apkKey(version.getDownloadUrl());
        Files.createDirectories(cache);
        Path file = cache.resolve(sha + ".apk");
        if (!validCache(file, sha, version.getSize())) {
            Path temp = Files.createTempFile(cache, "incoming-", ".part");
            try {
                cos.cacheApk(key, temp);
                if (!validCache(temp, sha, version.getSize())) throw new IOException("APK integrity mismatch");
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally { Files.deleteIfExists(temp); }
        }
        cos.makeApkPrivate(key);
        // A public bucket policy can override a private object ACL. Do not issue links if anonymous access still succeeds.
        var connection = (java.net.HttpURLConnection) java.net.URI.create(version.getDownloadUrl()).toURL().openConnection();
        boolean privateObject;
        try {
            connection.setRequestMethod("HEAD"); connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
            privateObject = connection.getResponseCode() == 403;
        } finally { connection.disconnect(); }
        if (!privateObject) throw new IOException("APK anonymous access remains enabled");
        // Retain only the current package. Tickets for a changed version are rejected before counting.
        try (var files = Files.list(cache)) {
            for (Path old : files.filter(p -> p.getFileName().toString().matches("[a-f0-9]{64}\\.apk") && !p.equals(file)).toList()) Files.deleteIfExists(old);
        }
        return new Package(version.getVersionCode(), sha, version.getSize(), key, file);
    }
    private boolean validCache(Path file, String sha, long size) throws Exception {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) != size) return false;
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) { byte[] bytes = new byte[65536]; int n; while ((n = input.read(bytes)) != -1) digest.update(bytes, 0, n); }
        return HexFormat.of().formatHex(digest.digest()).equals(sha);
    }
    private Package currentPackage() {
        var version = versions.getPublished(); var value = ready;
        if (value == null || value.version != version.getVersionCode() || !value.sha.equals(version.getSha256())) {
            refresh(); throw new BusinessException(503, "安装包正在准备，请稍后再试");
        }
        return value;
    }
    public record Ticket(String downloadUrl, long expiresAt) {}
    public Ticket issue(long userId, HttpServletRequest request) {
        requireUser(userId);
        Package pack = currentPackage();
        byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        long now = store.now(), expires = now + 60_000;
        store.transaction(() -> {
            var policy = store.lock();
            long recent = store.db.queryForObject("SELECT COUNT(*) FROM sys_app_download_ticket WHERE user_id=? AND created_at>?", Long.class, userId, now - 60_000);
            if (recent >= 5) throw new BusinessException(429, "下载请求过于频繁，请一分钟后再试");
            store.db.update("INSERT INTO sys_app_download_ticket(token_hash,user_id,ip_hash,version_code,sha256,epoch,created_at,expires_at) VALUES (?,?,?,?,?,?,?,?)", hash(token), userId, hash(IpUtil.clientIp(request)), pack.version, pack.sha, policy.get("epoch"), now, expires);
            return null;
        });
        return new Ticket("/api/app/download/file?ticket=" + token, expires);
    }
    record Redemption(long userId, String mode, java.time.LocalDate day) {}
    Redemption redeem(String token, HttpServletRequest request, Package pack) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw new BusinessException(403, "下载凭证无效");
        return store.transaction(() -> {
            var policy = store.lock();
            var matches = store.db.queryForList("SELECT * FROM sys_app_download_ticket WHERE token_hash=? FOR UPDATE", hash(token));
            if (matches.isEmpty()) throw new BusinessException(403, "下载凭证无效");
            var t = matches.getFirst();
            if (t.get("used_at") != null || AppDownloadStore.number(t.get("expires_at")) <= store.now()) throw new BusinessException(410, "下载凭证已使用或过期，请重新下载");
            if (!Objects.equals(t.get("epoch"), policy.get("epoch")) || AppDownloadStore.number(t.get("version_code")) != pack.version || !pack.sha.equals(t.get("sha256"))) throw new BusinessException(409, "版本或下载方式已变化，请重新下载");
            if (!Objects.equals(t.get("ip_hash"), hash(IpUtil.clientIp(request)))) throw new BusinessException(403, "下载环境已变化，请重新下载");
            long userId = AppDownloadStore.number(t.get("user_id")); requireUser(userId);
            var day = store.today();
            boolean sameDay = day.toString().equals(Objects.toString(policy.get("guard_date")));
            long count = (sameDay ? AppDownloadStore.number(policy.get("guard_count")) : 0) + 1;
            boolean alerted = sameDay && AppDownloadStore.flag(policy.get("alerted"));
            String mode = Objects.toString(policy.get("mode"));
            boolean auto = AppDownloadStore.flag(policy.get("auto_switched"));
            if (count > AppDownloadStore.number(policy.get("proxy_limit")) && "COS".equals(mode)) { mode = "SERVER"; auto = true; }
            if (!alerted && count > AppDownloadStore.number(policy.get("alert_limit"))) {
                mail.enqueueDownloadAlert(policy.get("alert_email").toString(), day + ":" + policy.get("epoch"), day.toString(), count, (int) AppDownloadStore.number(policy.get("alert_limit")));
                alerted = true;
            }
            store.db.update("UPDATE sys_app_download_policy SET mode=?,auto_switched=?,guard_date=?,guard_count=?,alerted=?,revision=? WHERE id=1", mode, auto, day, count, alerted, AppDownloadStore.uuid());
            store.db.update("UPDATE sys_app_download_ticket SET used_at=? WHERE token_hash=?", store.now(), hash(token));
            if (store.db.queryForObject("SELECT COUNT(*) FROM sys_app_download_day WHERE download_date=?", Integer.class, day) == 0)
                store.db.update("INSERT INTO sys_app_download_day(download_date) VALUES (?)", day);
            store.db.update("UPDATE sys_app_download_day SET total_count=total_count+1,cos_count=cos_count+?,server_count=server_count+?,estimated_bytes=estimated_bytes+? WHERE download_date=?", "COS".equals(mode) ? 1 : 0, "SERVER".equals(mode) ? 1 : 0, pack.size, day);
            return new Redemption(userId, mode, day);
        });
    }
    public void download(String token, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!"GET".equals(request.getMethod())) throw new BusinessException(405, "仅支持 GET 下载");
        if (!transfers.tryAcquire()) throw new BusinessException(429, "下载通道繁忙，请稍后再试");
        Redemption result = null;
        try {
            Package pack = currentPackage();
            result = redeem(token, request, pack);
            request.setAttribute(com.sap.aspect.OperationLogAspect.ACTOR, result.userId);
            response.setHeader("Cache-Control", "no-store, private"); response.setHeader("Referrer-Policy", "no-referrer"); response.setHeader("X-Content-Type-Options", "nosniff");
            traffic.recordDownloadForUser(pack.size, result.userId);
            if ("COS".equals(result.mode)) { response.sendRedirect(cos.signedApk(pack.key, pack.version)); return; }
            response.setContentType("application/vnd.android.package-archive");
            response.setHeader("Content-Disposition", "attachment; filename=\"sap-" + pack.version + ".apk\"");
            response.setContentLengthLong(pack.size);
            response.setHeader("Accept-Ranges", "none");
            try (var input = Files.newInputStream(pack.file)) { input.transferTo(response.getOutputStream()); }
        } catch (IOException | RuntimeException e) {
            if (result != null) store.db.update("UPDATE sys_app_download_day SET failed_count=failed_count+1 WHERE download_date=?", result.day);
            throw e;
        } finally { transfers.release(); }
    }
    public void current(long userId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String url = issue(userId, request).downloadUrl();
        download(url.substring(url.indexOf("ticket=") + 7), request, response);
    }
    /** Old Android clients wrap the metadata URL in /file/go. No arbitrary same-origin redirect is accepted. */
    public boolean isLegacyDownload(String url) {
        if ("/api/app/download/current".equals(url)) return true;
        try {
            var uri = java.net.URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && cos.isAllowedPublicHost(uri.getHost()) && uri.getPath() != null && uri.getPath().startsWith("/apk/");
        } catch (Exception invalid) { return false; }
    }
    public void legacy(String url, long userId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!"/api/app/download/current".equals(url) && !cos.apkKey(url).equals(currentPackage().key)) throw new BusinessException(409, "安装包已更新，请重新检查 App 更新");
        current(userId, request, response);
    }
    private void requireUser(long id) {
        var user = users.selectById(id);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) throw new BusinessException(403, "当前账号不可下载");
    }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @Scheduled(fixedDelay = 3_600_000) public void pruneTickets() { store.db.update("DELETE FROM sys_app_download_ticket WHERE expires_at<?", store.now() - 86_400_000); }
    record Package(int version, String sha, long size, String key, Path file) {}
}
