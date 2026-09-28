/*
 * Local security audit proofs, 2026-09-06.
 * Uses existing compiled project classes, in-memory Sa-Token and mocked mappers.
 * These checks demonstrate vulnerable behavior; PASS does not mean secure.
 * Never connects to production, sends email, or changes application data.
 */
import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.spring.SaTokenContextForSpringInJakartaServlet;
import cn.dev33.satoken.stp.*;
import com.sap.BaseUnitTest;
import com.sap.common.BusinessException;
import com.sap.controller.*;
import com.sap.dto.LoginDTO;
import com.sap.entity.*;
import com.sap.mapper.*;
import com.sap.service.*;
import com.sap.util.PasswordUtil;
import com.sap.vo.AppVersionVO;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.*;
import org.springframework.web.method.HandlerMethod;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class SecurityAuditProof extends BaseUnitTest {
    static final Map<Long, List<String>> ROLES = new HashMap<>();
    static MockHttpServletRequest request;
    static MockHttpServletResponse response;
    static int passed;
    static int failed;
    interface Proof { void run() throws Exception; }
    static void proof(String name, Proof action) {
        try { action.run(); passed++; System.out.println("CONFIRMED " + name); }
        catch (Throwable e) { failed++; System.out.println("NOT_CONFIRMED " + name + " " + e.getClass().getSimpleName() + ": " + e.getMessage()); }
    }
    static void require(boolean value) { if (!value) throw new AssertionError("Proof expectation not met"); }
    static void inject(Object obj, String name, Object value) { ReflectionTestUtils.setField(obj, name, value); }
    static void context(String token) {
        request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        if (token != null) request.addHeader("sap-token", token);
        response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
    }
    static String token(long user, String... roles) {
        ROLES.put(user, List.of(roles));
        context(null);
        return StpUtil.stpLogic.createLoginSession(user, new SaLoginModel().setDevice("app").setTimeout(-1).setActiveTimeout(-1));
    }
    static void gate(Object controller, String method, Class<?>... params) throws Exception {
        require(new SaInterceptor(h -> StpUtil.checkLogin()).preHandle(request, response,
                new HandlerMethod(controller, controller.getClass().getMethod(method, params))));
    }
    static User user(long id) {
        User user = new User(); user.setId(id); user.setStudentId("AUDIT" + id);
        user.setName("Audit fixture"); user.setStatus(1); user.setPassword(PasswordUtil.encode("audit-old-password"));
        return user;
    }
    static Position position(int id, int system, int role) {
        Position p = new Position(); p.setId(id); p.setIsSystem(system); p.setRoleCode(role);
        p.setPositionName("Audit position " + id); p.setMaxCount(1); return p;
    }

    public static void main(String[] args) throws Exception {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("ROOT");
        root.setLevel(ch.qos.logback.classic.Level.ERROR);
        SaTokenConfig config = new SaTokenConfig();
        config.setTokenName("sap-token"); config.setIsPrint(false); config.setIsReadCookie(false); config.setIsShare(false);
        SaManager.setConfig(config);
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        SaManager.setSaTokenContext(new SaTokenContextForSpringInJakartaServlet());
        SaManager.setStpInterface(new StpInterface() {
            public List<String> getPermissionList(Object id, String type) { return List.of(); }
            public List<String> getRoleList(Object id, String type) { return ROLES.getOrDefault(Long.parseLong(id.toString()), List.of()); }
        });

        proof("settings_guest_reads_sensitive_key_variants", () -> {
            SettingService settings = mock(SettingService.class);
            when(settings.getValue(anyString())).thenAnswer(inv -> {
                String key = inv.getArgument(0);
                return Set.of("cos_secret_key", "cos_secret_id", "email_smtp_password").contains(key.trim().toLowerCase(Locale.ROOT))
                        ? "AUDIT_MARKER_NOT_A_REAL_SECRET" : null;
            });
            SettingController controller = new SettingController(); inject(controller, "settingService", settings);
            context(token(9101, "4")); gate(controller, "getValue", String.class);
            boolean blocked = false;
            try { controller.getValue("cos_secret_key"); } catch (BusinessException expected) { blocked = true; }
            require(blocked);
            for (String key : List.of("COS_SECRET_KEY", "cos_secret_key ", "email_smtp_password")) {
                require("AUDIT_MARKER_NOT_A_REAL_SECRET".equals(controller.getValue(key).getData()));
            }
        });

        proof("settings_president_bypasses_superadmin_only_cloud_setting", () -> {
            SettingService settings = mock(SettingService.class);
            SettingController controller = new SettingController(); inject(controller, "settingService", settings);
            context(token(9102, "1")); gate(controller, "update", Setting.class);
            Setting setting = new Setting(); setting.setSettingKey("GUEST_ACCESS_LEVEL"); setting.setSettingValue("2");
            controller.update(setting); verify(settings).updateSetting(setting);
        });

        proof("president_position_changeover_grants_superadmin", () -> {
            PositionMapper positions = mock(PositionMapper.class);
            PositionService ps = new PositionService(); inject(ps, "positionMapper", positions);
            PositionController pc = new PositionController(); inject(pc, "positionService", ps);
            String caller = token(9201, "1"); context(caller); gate(pc, "add", Position.class);
            Position malicious = position(99, 0, 0); pc.add(malicious);
            verify(positions).insert(malicious); require(malicious.getRoleCode() == 0);

            TermService ts = new TermService();
            SettingMapper settings = mock(SettingMapper.class); Setting grade = new Setting(); grade.setSettingValue("2026");
            when(settings.selectOne(any())).thenReturn(grade);
            when(positions.selectList(any())).thenReturn(List.of(position(1, 1, 1), position(2, 1, 2), malicious));
            UserRoleMapper roles = mock(UserRoleMapper.class); when(roles.selectCount(any())).thenReturn(0L);
            AtomicBoolean elevated = new AtomicBoolean();
            when(roles.insert(any(UserRole.class))).thenAnswer(inv -> { UserRole r = inv.getArgument(0);
                if (r.getUserId() == 9201L && r.getRoleCode() == 0) elevated.set(true); return 1; });
            inject(ts, "settingMapper", settings); inject(ts, "positionMapper", positions); inject(ts, "userRoleMapper", roles);
            inject(ts, "termMapper", mock(TermMapper.class)); inject(ts, "studyActivityMapper", mock(StudyActivityMapper.class));
            inject(ts, "joinService", mock(JoinService.class)); inject(ts, "cacheService", mock(CacheService.class));
            TermController tc = new TermController(); inject(tc, "termService", ts);
            context(caller); gate(tc, "changeover", List.class);
            tc.changeover(List.of(Map.of("positionId", 1, "userIds", List.of(9202)),
                    Map.of("positionId", 2, "userIds", List.of(9203)), Map.of("positionId", 99, "userIds", List.of(9201))));
            require(elevated.get());
        });

        proof("role2_changes_superadmin_recovery_address", () -> {
            User superAdmin = user(9250); superAdmin.setQq("original-recovery-recipient");
            UserMapper users = mock(UserMapper.class); when(users.selectById(9250L)).thenReturn(superAdmin);
            UserService service = new UserService(); inject(service, "userMapper", users);
            inject(service, "cacheService", mock(CacheService.class));
            UserController controller = new UserController(); inject(controller, "userService", service);

            context(token(9251, "2")); gate(controller, "update", Long.class, User.class);
            User patch = new User(); patch.setQq("attacker-controlled-recipient"); patch.setStatus(1);
            controller.update(9250L, patch);

            require("attacker-controlled-recipient".equals(superAdmin.getQq()));
            verify(users).updateById(superAdmin);
        });

        proof("password_reset_and_disable_leave_old_token_valid", () -> {
            User victim = user(9302); UserMapper users = mock(UserMapper.class);
            when(users.selectOne(any())).thenReturn(victim); when(users.selectById(9302L)).thenReturn(victim);
            UserService service = new UserService(); inject(service, "userMapper", users);
            inject(service, "cacheService", mock(CacheService.class));
            String oldToken = token(9302, "3"); String admin = token(9301, "0"); context(admin);
            service.resetPassword(victim.getStudentId(), "audit-replacement-password");
            require(PasswordUtil.matches("audit-replacement-password", victim.getPassword()));
            require("9302".equals(String.valueOf(StpUtil.stpLogic.getLoginIdByToken(oldToken))));
            User patch = new User(); patch.setStatus(0); service.updateUser(9302L, patch); require(victim.getStatus() == 0);
            AuthService auth = new AuthService(); CacheService cache = mock(CacheService.class);
            when(cache.getUserById(9302L)).thenReturn(victim);
            UserRoleMapper roles = mock(UserRoleMapper.class); when(roles.selectRoleCodesByUserId(9302L)).thenReturn(List.of(3));
            TermMapper terms = mock(TermMapper.class); when(terms.selectList(any())).thenReturn(List.of());
            inject(auth, "cacheService", cache); inject(auth, "userRoleMapper", roles); inject(auth, "termMapper", terms);
            AuthController controller = new AuthController(); inject(controller, "authService", auth);
            context(oldToken); gate(controller, "infoLight");
            require(controller.infoLight().getCode() == 200);
        });

        proof("guest_denied_note_detail_but_allowed_pdf", () -> {
            Note note = new Note(); note.setId(9401L); note.setTitle("Audit note"); note.setContent("Private fixture");
            NoteMapper mapper = mock(NoteMapper.class); when(mapper.selectById(9401L)).thenReturn(note);
            PdfService pdf = mock(PdfService.class); when(pdf.markdownToPdf(anyString(), anyString())).thenReturn("%PDF-audit-only".getBytes());
            NoteController controller = new NoteController(); inject(controller, "noteMapper", mapper);
            inject(controller, "pdfService", pdf); inject(controller, "noteService", mock(NoteService.class));
            context(token(9402, "4")); gate(controller, "detail", Long.class);
            require(controller.detail(9401L).getCode() == 403);
            gate(controller, "downloadPdf", Long.class, jakarta.servlet.http.HttpServletResponse.class);
            controller.downloadPdf(9401L, response);
            require(response.getStatus() == 200 && response.getContentAsByteArray().length > 0);
        });

        proof("guest_reads_study_roster_scores_and_files", () -> {
            Map<String, Object> privateRow = Map.of(
                    "studentId", "AUDIT-STUDENT-ID",
                    "score", 98,
                    "materials", List.of(Map.of("fileUrl", "https://audit.invalid/private-submission")));
            StudyService service = mock(StudyService.class);
            when(service.getCycleDetail(1L, 1)).thenReturn(List.of(privateRow));
            StudyController controller = new StudyController(); inject(controller, "studyService", service);

            context(token(9403, "4")); gate(controller, "getCycleDetail", Long.class, Integer.class);
            Object data = controller.getCycleDetail(1L, 1).getData();
            require(List.of(privateRow).equals(data));
        });

        proof("guest_gets_restricted_app_download_url", () -> {
            AppVersionVO version = new AppVersionVO();
            version.setDownloadUrl("https://audit.invalid/member-only.apk");
            AppVersionService service = mock(AppVersionService.class); when(service.getLatest()).thenReturn(version);
            AppVersionController controller = new AppVersionController(); inject(controller, "appVersionService", service);

            context(token(9404, "4")); gate(controller, "latest");
            require("https://audit.invalid/member-only.apk".equals(controller.latest().getData().getDownloadUrl()));
        });

        proof("cos_proxy_accepts_unrelated_bucket", () -> {
            CosService service = new CosService(); inject(service, "settingService", mock(SettingService.class));
            String host = "audit-unrelated-1250000000.cos.ap-guangzhou.myqcloud.com";
            require(service.isAllowedPublicHost(host)); require(!service.isOwnedPublicHost(host));
        });

        proof("anonymous_ping_reaches_database_counter", () -> {
            ApiRequestStatMapper counts = mock(ApiRequestStatMapper.class);
            TrafficService traffic = new TrafficService(); inject(traffic, "apiRequestStatMapper", counts);
            inject(traffic, "userMapper", mock(UserMapper.class));
            com.sap.config.ApiStatInterceptor interceptor = new com.sap.config.ApiStatInterceptor();
            inject(interceptor, "trafficService", traffic);

            context(null);
            request.setMethod("GET");
            request.setRequestURI("/api/ping");
            request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/ping");
            interceptor.afterCompletion(request, response, new Object(), null);

            verify(counts).upsert(any(), eq(0L), eq("匿名"), eq("/api/ping"), eq("GET"));
        });

        proof("login_account_whitespace_bypasses_failure_lock", () -> {
            AuthService service = new AuthService(); UserMapper mapper = mock(UserMapper.class);
            User victim = user(9501); when(mapper.selectOne(any())).thenReturn(victim); inject(service, "userMapper", mapper);
            context(null);
            LoginDTO dto = new LoginDTO(); dto.setStudentId(victim.getStudentId()); dto.setPassword("wrong-audit-password");
            for (int i = 0; i < 5; i++) { try { service.login(dto); } catch (BusinessException expected) {} }
            boolean locked = false; try { service.login(dto); } catch (BusinessException ex) { locked = ex.getMessage().contains("锁定"); }
            require(locked); dto.setStudentId(victim.getStudentId() + " ");
            boolean newAttempt = false; try { service.login(dto); } catch (BusinessException ex) { newAttempt = ex.getMessage().equals("账号或密码错误"); }
            require(newAttempt);
        });

        System.out.println("RESULT confirmed=" + passed + " not_confirmed=" + failed);
        System.exit(failed == 0 ? 0 : 1);
    }
}
