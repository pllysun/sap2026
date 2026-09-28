package com.sap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sap.common.BusinessException;
import com.sap.dto.EmailTemplateDTO;
import com.sap.entity.EmailTemplate;
import com.sap.entity.Setting;
import com.sap.jw.util.AesUtil;
import com.sap.mapper.EmailTemplateMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QQ SMTP 配置与 HTML 邮件模板服务。
 * <p>SMTP 授权码使用和教务密码相同的 AES-GCM 机制加密保存；前端读取配置时永远不会拿到授权码明文。</p>
 */
@Service
public class EmailService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.sap.service.mail.MailStore mailStore;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.sap.service.mail.MailQueue> mailQueue;

    private static final String KEY_HOST = "email_smtp_host";
    private static final String KEY_PORT = "email_smtp_port";
    private static final String KEY_USERNAME = "email_smtp_username";
    private static final String KEY_PASSWORD = "email_smtp_password";
    private static final String KEY_FROM_NAME = "email_smtp_from_name";
    private static final String KEY_SSL = "email_smtp_ssl";
    private static final String KEY_STARTTLS = "email_smtp_starttls";
    private static final String KEY_ENABLED = "email_smtp_enabled";
    private static final String PASSWORD_MASK = "********";
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}")
            ;
    private static final Pattern UNSAFE_HTML = Pattern.compile(
            "(?is)<\\s*script|javascript\\s*:|on[a-z]+\\s*=\\s*['\"]");

    private final SettingService settingService;
    private final EmailTemplateMapper templateMapper;

    @Value("${jw.aes-key:change-me-in-prod-please-32bytes!}")
    private String encryptionSecret;

    public EmailService(SettingService settingService, EmailTemplateMapper templateMapper) {
        this.settingService = settingService;
        this.templateMapper = templateMapper;
    }

    public Map<String, Object> getConfig() {
        String encryptedPassword = settingService.getValue(KEY_PASSWORD);
        String username = settingService.getValue(KEY_USERNAME);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("host", valueOrDefault(settingService.getValue(KEY_HOST), "smtp.qq.com"));
        result.put("port", parsePort(settingService.getValue(KEY_PORT)));
        result.put("username", nullToEmpty(username));
        result.put("fromName", nullToEmpty(settingService.getValue(KEY_FROM_NAME)));
        result.put("ssl", boolValue(settingService.getValue(KEY_SSL), true));
        result.put("starttls", boolValue(settingService.getValue(KEY_STARTTLS), false));
        result.put("enabled", boolValue(settingService.getValue(KEY_ENABLED), true));
        result.put("passwordSet", !isBlank(encryptedPassword));
        result.put("configured", !isBlank(username) && !isBlank(encryptedPassword));
        result.put("passwordMask", PASSWORD_MASK);
        return result;
    }

    public void saveConfig(Map<String, String> request) {
        String host = valueOrDefault(request.get("host"), "smtp.qq.com").trim();
        String port = valueOrDefault(request.get("port"), "465").trim();
        String username = nullToEmpty(request.get("username")).trim();
        String fromName = nullToEmpty(request.get("fromName")).trim();
        validatePort(port);
        if (isBlank(host)) throw new BusinessException(400, "SMTP 服务器不能为空");
        if (isBlank(username) || !username.contains("@")) {
            throw new BusinessException(400, "请输入有效的 QQ 邮箱账号");
        }
        saveSetting(KEY_HOST, host, "邮件 SMTP 服务器");
        saveSetting(KEY_PORT, port, "邮件 SMTP 端口");
        saveSetting(KEY_USERNAME, username, "邮件 SMTP 发件账号");
        saveSetting(KEY_FROM_NAME, fromName, "邮件默认发件人名称");
        saveSetting(KEY_SSL, String.valueOf(boolValue(request.get("ssl"), true)), "邮件 SMTP SSL 开关");
        saveSetting(KEY_STARTTLS, String.valueOf(boolValue(request.get("starttls"), false)), "邮件 SMTP STARTTLS 开关");
        saveSetting(KEY_ENABLED, String.valueOf(boolValue(request.get("enabled"), true)), "邮件发送总开关");

        String password = request.get("password");
        if (!isBlank(password) && !PASSWORD_MASK.equals(password.trim())) {
            saveSetting(KEY_PASSWORD, AesUtil.encrypt(encryptionSecret, password), "邮件 SMTP 授权码（加密）");
        }
        mailQueue.getObject().kick();
    }

    public void testConnection() {
        JavaMailSenderImpl sender = buildSender(true);
        try {
            sender.testConnection();
        } catch (MessagingException | RuntimeException e) {
            throw new BusinessException("SMTP 连通性测试失败：" + safeMessage(e));
        }
    }

    public List<Map<String, Object>> listTemplates() {
        return templateMapper.selectList(new LambdaQueryWrapper<EmailTemplate>()
                        .orderByDesc(EmailTemplate::getUpdatedAt)
                        .orderByAsc(EmailTemplate::getId))
                .stream().map(this::toView).toList();
    }

    public Map<String, Object> getTemplate(Long id) {
        EmailTemplate template = requireTemplate(id);
        return toView(template);
    }

    public Map<String, Object> createTemplate(EmailTemplateDTO dto) {
        validateTemplate(dto);
        EmailTemplate duplicate = templateMapper.selectAnyByKey(dto.getTemplateKey().trim());
        if (duplicate != null && !Integer.valueOf(1).equals(duplicate.getDeleted())) {
            throw new BusinessException(400, "模板标识已存在");
        }
        EmailTemplate entity = duplicate != null ? duplicate : new EmailTemplate();
        copy(dto, entity);
        entity.setDeleted(0);
        if (entity.getEnabled() == null) entity.setEnabled(true);
        if (duplicate == null) templateMapper.insert(entity);
        else templateMapper.restore(entity.getId(), entity.getTemplateKey(), entity.getTemplateName(),
                entity.getSubject(), entity.getHtmlContent(), entity.getDescription(), entity.getEnabled());
        return toView(entity);
    }

    public Map<String, Object> updateTemplate(Long id, EmailTemplateDTO dto) {
        return mailStore.transaction(() -> {
        mailStore.lock();
        validateTemplate(dto);
        mailQueue.getObject().validateBoundTemplate(id, Map.of("subject",dto.getSubject(),"htmlContent",dto.getHtmlContent()));
        EmailTemplate entity = requireTemplate(id);
        EmailTemplate duplicate = templateMapper.selectAnyByKey(dto.getTemplateKey().trim());
        if (duplicate != null && !Objects.equals(duplicate.getId(), id)
                && !Integer.valueOf(1).equals(duplicate.getDeleted())) {
            throw new BusinessException(400, "模板标识已存在");
        }
        copy(dto, entity);
        if (entity.getEnabled() == null) entity.setEnabled(true);
        templateMapper.updateById(entity);
        return toView(entity);
        });
    }

    public void deleteTemplate(Long id) {
        mailStore.transaction(() -> {
        mailStore.lock();
        mailStore.requireUnbound(id);
        requireTemplate(id);
        templateMapper.deleteById(id);
        return null;
        });
    }

    public Map<String, Object> preview(Long id, Map<String, Object> variables) {
        EmailTemplate template = requireTemplate(id);
        return render(template.getSubject(), template.getHtmlContent(), variables);
    }

    /** 发送当前编辑器草稿；若未传内容，则从已保存的模板读取。 */
    public void sendTest(Map<String, Object> request) {
        String to = stringValue(request.get("to"));
        if (isBlank(to) || !to.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new BusinessException(400, "请输入有效的收件邮箱");
        }
        String subject = stringValue(request.get("subject"));
        String html = stringValue(request.get("htmlContent"));
        if (isBlank(html)) {
            Object id = request.get("templateId");
            if (id == null) throw new BusinessException(400, "请选择邮件模板或填写 HTML 内容");
            EmailTemplate template = requireTemplate(Long.valueOf(String.valueOf(id)));
            subject = template.getSubject();
            html = template.getHtmlContent();
        }
        if (isBlank(subject)) throw new BusinessException(400, "邮件主题不能为空");
        Map<String, Object> variables = variables(request.get("variables"));
        Map<String, Object> rendered = render(subject, html, variables);
        mailQueue.getObject().enqueueTest(to, String.valueOf(rendered.get("subject")), String.valueOf(rendered.get("html")),
                cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong());
    }

    public boolean sendingEnabled() {
        return boolValue(settingService.getValue(KEY_ENABLED), true);
    }

    /** 仅由数据库队列工作器调用。成功表示 SMTP 接受，不代表最终收件。 */
    public void deliver(String to, String subject, String html) {
        JavaMailSenderImpl sender = buildSender(false);
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            String username = settingService.getValue(KEY_USERNAME);
            String fromName = settingService.getValue(KEY_FROM_NAME);
            helper.setFrom(username, isBlank(fromName) ? username : fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
        } catch (Exception e) {
            throw new BusinessException("邮件发送失败：" + e.getClass().getSimpleName());
        }
    }

    private JavaMailSenderImpl buildSender(boolean forTest) {
        if (!forTest && !boolValue(settingService.getValue(KEY_ENABLED), true)) {
            throw new BusinessException("邮件发送功能已关闭，请先在邮件管理中开启");
        }
        String username = settingService.getValue(KEY_USERNAME);
        String encryptedPassword = settingService.getValue(KEY_PASSWORD);
        if (isBlank(username) || isBlank(encryptedPassword)) {
            throw new BusinessException("QQ 邮箱 SMTP 尚未配置完整");
        }
        String password;
        try {
            password = AesUtil.decrypt(encryptionSecret, encryptedPassword);
        } catch (RuntimeException e) {
            throw new BusinessException("邮件授权码解密失败，请重新保存 SMTP 配置");
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(valueOrDefault(settingService.getValue(KEY_HOST), "smtp.qq.com"));
        sender.setPort(parsePort(settingService.getValue(KEY_PORT)));
        sender.setUsername(username);
        sender.setPassword(password);
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.ssl.enable", String.valueOf(boolValue(settingService.getValue(KEY_SSL), true)));
        properties.put("mail.smtp.starttls.enable", String.valueOf(boolValue(settingService.getValue(KEY_STARTTLS), false)));
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");
        return sender;
    }

    public Map<String, Object> render(String subject, String html, Map<String, Object> variables) {
        Map<String, Object> safeVariables = variables == null ? Collections.emptyMap() : variables;
        return Map.of(
                "subject", replace(subject, safeVariables, false),
                "html", replace(html, safeVariables, true));
    }

    private String replace(String source, Map<String, Object> variables, boolean html) {
        Matcher matcher = VARIABLE.matcher(source == null ? "" : source);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object raw = variables.get(key);
            String value = raw == null ? "" : String.valueOf(raw);
            if (html) value = HtmlUtils.htmlEscape(value);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private Map<String, Object> toView(EmailTemplate template) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", template.getId());
        result.put("templateKey", template.getTemplateKey());
        result.put("templateName", template.getTemplateName());
        result.put("subject", template.getSubject());
        result.put("htmlContent", template.getHtmlContent());
        result.put("description", template.getDescription());
        result.put("enabled", template.getEnabled() == null || template.getEnabled());
        result.put("variables", variables(template.getSubject() + "\n" + template.getHtmlContent()));
        result.put("createdAt", template.getCreatedAt());
        result.put("updatedAt", template.getUpdatedAt());
        return result;
    }

    private List<String> variables(String source) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = VARIABLE.matcher(source == null ? "" : source);
        while (matcher.find()) values.add(matcher.group(1));
        return new ArrayList<>(values);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> variables(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) return Collections.emptyMap();
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private EmailTemplate requireTemplate(Long id) {
        EmailTemplate template = templateMapper.selectById(id);
        if (template == null) throw new BusinessException(404, "邮件模板不存在");
        return template;
    }

    private void validateTemplate(EmailTemplateDTO dto) {
        if (dto == null || isBlank(dto.getTemplateKey()) || isBlank(dto.getTemplateName())
                || isBlank(dto.getSubject()) || isBlank(dto.getHtmlContent())) {
            throw new BusinessException(400, "模板标识、名称、主题和 HTML 内容不能为空");
        }
        if (UNSAFE_HTML.matcher(dto.getHtmlContent()).find()) {
            throw new BusinessException(400, "HTML 模板不允许包含脚本、事件处理器或 javascript 链接");
        }
        if (!isBlank(dto.getEventKey())) {
            com.sap.service.mail.MailQueue.validateContract(
                    com.sap.service.mail.MailHook.parse(dto.getEventKey()),
                    Map.of("subject",dto.getSubject(),"htmlContent",dto.getHtmlContent()));
        }
    }

    private void copy(EmailTemplateDTO dto, EmailTemplate entity) {
        entity.setTemplateKey(dto.getTemplateKey().trim());
        entity.setTemplateName(dto.getTemplateName().trim());
        entity.setSubject(dto.getSubject().trim());
        entity.setHtmlContent(dto.getHtmlContent());
        entity.setDescription(dto.getDescription() == null ? "" : dto.getDescription().trim());
        entity.setEnabled(dto.getEnabled() == null || dto.getEnabled());
    }

    private void saveSetting(String key, String value, String description) {
        Setting setting = new Setting();
        setting.setSettingKey(key);
        setting.setSettingValue(value == null ? "" : value);
        setting.setDescription(description);
        settingService.updateSetting(setting);
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value == null ? "465" : value);
            return port >= 1 && port <= 65535 ? port : 465;
        } catch (NumberFormatException e) {
            return 465;
        }
    }

    private static void validatePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "SMTP 端口必须是 1-65535 之间的数字");
        }
    }

    private static boolean boolValue(Object value, boolean fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String valueOrDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (isBlank(message)) return throwable.getClass().getSimpleName();
        return message.replaceAll("(?i)(password|授权码|credential)[^,; ]*", "$1=***");
    }
}
