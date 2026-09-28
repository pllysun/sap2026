package com.sap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.common.BusinessException;
import com.sap.dto.RegistrationProtectionConfig;
import com.sap.entity.Setting;
import com.sap.mapper.SettingMapper;
import com.sap.util.IpUtil;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.*;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/** 策略分组存于现有设置表，整组提交；内存只缓存最后一次有效的不可变快照。 */
@Service
public class RegistrationProtectionSettingsService {
    public static final String PREFIX = "registration_protection_";
    private static final String CAPTCHA = PREFIX + "captcha";
    private static final String QUOTAS = PREFIX + "quotas";
    private static final String REQUESTS = PREFIX + "requests";
    private static final String PROXIES = PREFIX + "trusted_proxies";
    private static final String REVISION = PREFIX + "revision";
    private static final Set<String> KEYS = Set.of(CAPTCHA, QUOTAS, REQUESTS, PROXIES, REVISION);
    private static final String REQUEST_ATTRIBUTE = RegistrationProtectionSettingsService.class.getName();
    private static final long RELOAD_MILLIS = 5_000;
    private static final Logger log = LoggerFactory.getLogger(RegistrationProtectionSettingsService.class);

    private final SettingMapper mapper;
    private final ObjectMapper json;
    private final Validator validator;
    private volatile Snapshot cached;
    private long reloadAt;
    private LongSupplier clock = System::currentTimeMillis;

    public record View(String revision, RegistrationProtectionConfig config, RegistrationProtectionConfig defaults) {}
    public record Update(@NotBlank @Size(max = 64) String revision,
                         @NotNull @Valid RegistrationProtectionConfig config) {}
    private record Snapshot(String revision, RegistrationProtectionConfig config, Set<String> proxies) {
        View view() { return new View(revision, config, RegistrationProtectionConfig.defaults()); }
    }

    public RegistrationProtectionSettingsService(SettingMapper mapper, ObjectMapper json, Validator validator) {
        this.mapper = mapper;
        this.json = json;
        this.validator = validator;
    }

    @PostConstruct
    public void initialize() {
        Map<String, String> defaults = values(RegistrationProtectionConfig.defaults());
        defaults.put(REVISION, UUID.randomUUID().toString());
        defaults.forEach((key, value) -> {
            if (find(key, false) != null) return;
            Setting setting = new Setting();
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            setting.setDescription("注册防护配置（管理端系统设置维护）");
            try {
                mapper.insert(setting);
            } catch (DuplicateKeyException concurrentStartup) {
                // 多实例同时首次启动，只补缺项，绝不覆盖另一实例已存的策略。
                if (find(key, false) == null) throw concurrentStartup;
            }
        });
        // 初次启动必须有完整、合法的策略；运行期间数据库故障则保留最后有效版本。
        reload();
    }

    public RegistrationProtectionConfig current() { return requestSnapshot().config(); }
    public Set<String> trustedProxyAddresses() { return requestSnapshot().proxies(); }

    private Snapshot requestSnapshot() {
        var request = RequestContextHolder.getRequestAttributes();
        if (request != null) {
            var existing = request.getAttribute(REQUEST_ATTRIBUTE, 0);
            if (existing instanceof Snapshot snapshot) return snapshot;
        }
        Snapshot snapshot = cachedSnapshot();
        if (request != null) request.setAttribute(REQUEST_ATTRIBUTE, snapshot, 0);
        return snapshot;
    }

    private synchronized Snapshot cachedSnapshot() {
        long now = clock.getAsLong();
        if (cached == null || now >= reloadAt) {
            try {
                cached = readDatabase();
            } catch (RuntimeException e) {
                if (cached == null) throw new BusinessException(503, "注册防护配置暂不可用，请稍后再试");
                log.warn("注册防护配置刷新失败，继续使用最后有效版本 {}", cached.revision(), e);
            }
            reloadAt = now + RELOAD_MILLIS;
        }
        return cached;
    }

    /** 管理端读取强制访问数据库，不用缓存掩盖存储异常。 */
    public synchronized View reload() {
        cached = readDatabase();
        reloadAt = clock.getAsLong() + RELOAD_MILLIS;
        return cached.view();
    }

    @Transactional
    public View save(Update update) {
        if (update == null || update.config() == null || update.revision() == null) {
            throw new BusinessException(400, "请完整提交注册防护配置");
        }
        validate(update.config());
        // 锁定统一版本行，避免多位管理员同时保存时悄悄覆盖彼此的修改。
        Setting revision = find(REVISION, true);
        if (revision == null || !Objects.equals(revision.getSettingValue(), update.revision())) {
            throw new BusinessException(409, "配置已被其他管理员更新，请重新加载后修改");
        }
        Map<String, String> values = values(update.config());
        for (var entry : values.entrySet()) {
            Setting setting = find(entry.getKey(), false);
            if (setting == null) throw new BusinessException(409, "配置项缺失，请重新加载后修改");
            setting.setSettingValue(entry.getValue());
            if (mapper.updateById(setting) != 1) throw new BusinessException(409, "配置项已变化，请重新加载后修改");
        }
        String nextRevision = UUID.randomUUID().toString();
        revision.setSettingValue(nextRevision);
        if (mapper.updateById(revision) != 1) throw new BusinessException(409, "配置版本已变化，请重新加载后修改");
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { invalidate(); }
            });
        } else {
            invalidate();
        }
        return new View(nextRevision, update.config(), RegistrationProtectionConfig.defaults());
    }

    private synchronized void invalidate() { reloadAt = 0; }

    private Snapshot readDatabase() {
        Map<String, String> values = mapper.selectList(new LambdaQueryWrapper<Setting>()
                        .in(Setting::getSettingKey, KEYS)).stream()
                .collect(Collectors.toMap(Setting::getSettingKey, Setting::getSettingValue));
        if (!values.keySet().containsAll(KEYS)) throw new IllegalStateException("注册防护配置项不完整");
        try {
            var config = new RegistrationProtectionConfig(
                    json.readValue(values.get(CAPTCHA), RegistrationProtectionConfig.Captcha.class),
                    json.readValue(values.get(QUOTAS), RegistrationProtectionConfig.Quotas.class),
                    json.readValue(values.get(REQUESTS), RegistrationProtectionConfig.Requests.class),
                    values.get(PROXIES));
            validate(config);
            return new Snapshot(values.get(REVISION), config, IpUtil.parseTrustedProxies(config.trustedProxies()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("注册防护配置格式错误", e);
        }
    }

    private void validate(RegistrationProtectionConfig config) {
        var errors = validator.validate(config);
        if (!errors.isEmpty()) throw new BusinessException(400, errors.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage()).sorted().collect(Collectors.joining("；")));
        try {
            IpUtil.parseTrustedProxies(config.trustedProxies());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "可信代理仅支持确切的IPv4/IPv6地址，以逗号或换行分隔");
        }
    }

    private Map<String, String> values(RegistrationProtectionConfig config) {
        try {
            Map<String, String> values = new LinkedHashMap<>();
            values.put(CAPTCHA, json.writeValueAsString(config.captcha()));
            values.put(QUOTAS, json.writeValueAsString(config.quotas()));
            values.put(REQUESTS, json.writeValueAsString(config.requests()));
            values.put(PROXIES, config.trustedProxies());
            if (values.values().stream().anyMatch(value -> value.length() > 500)) {
                throw new BusinessException(400, "注册防护配置内容过长");
            }
            return values;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("注册防护配置无法序列化", e);
        }
    }

    private Setting find(String key, boolean forUpdate) {
        var query = new LambdaQueryWrapper<Setting>().eq(Setting::getSettingKey, key);
        if (forUpdate) query.last("FOR UPDATE");
        return mapper.selectOne(query);
    }
}
