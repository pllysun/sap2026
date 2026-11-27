package com.sap.mcp;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sap.common.BusinessException;
import com.sap.jw.entity.JwCredential;
import com.sap.jw.mapper.JwCredentialMapper;
import com.sap.jw.service.JwCredentialService;
import com.sap.mapper.McpApiKeyMapper;
import com.sap.mapper.UserMapper;
import com.sap.service.AppAccessService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class McpKeyService {
    private static final String PREFIX = "sap_mcp_";
    private static final Pattern KEY_FORMAT = Pattern.compile("sap_mcp_[A-Za-z0-9_-]{43}");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_KEYS = 10;

    private final McpApiKeyMapper keys;
    private final UserMapper users;
    private final JwCredentialMapper bindings;
    private final JwCredentialService credentials;
    private final AppAccessService access;

    public record CreateRequest(@NotBlank @Size(max = 40) String name,
                                @NotNull @Positive Long credentialId,
                                @NotEmpty @Size(max = 4) List<@NotBlank String> scopes) {}
    public record KeySummary(Long id, String name, String prefix, Long credentialId,
                             List<String> scopes, LocalDateTime createdAt) {}
    public record BindingOption(Long id, String account, String remark) {}
    public record KeySettings(int limit, boolean canCreate, List<KeySummary> keys, List<BindingOption> bindings) {}
    public record CreatedKey(KeySummary key, String secret) {}
    record Principal(Long userId, String account, List<String> scopes) {
        boolean permits(McpScope scope) {
            return scopes.contains(scope.value);
        }
    }

    public KeySettings settings(long userId) {
        var summaries = keys.selectList(Wrappers.<McpApiKey>lambdaQuery()
                .eq(McpApiKey::getUserId, userId).orderByDesc(McpApiKey::getId))
                .stream().map(McpKeyService::summary).toList();
        boolean canCreate = access.hasFullAccess(userId);
        List<BindingOption> options = canCreate ? credentials.listByUser(userId).stream()
                .filter(binding -> !Integer.valueOf(0).equals(binding.getStatus()))
                .map(binding -> new BindingOption(binding.getId(), binding.getJwAccount(), binding.getRemark()))
                .toList() : List.of();
        return new KeySettings(MAX_KEYS, canCreate, summaries, options);
    }

    @Transactional
    public CreatedKey create(long userId, CreateRequest request) {
        // Lock before reading the quota so concurrent creates for this user see committed inserts.
        if (users.lockActiveUserById(userId) == null) {
            throw new BusinessException(403, "当前账号无法创建 MCP 密钥");
        }
        requireAccess(userId);
        if (binding(userId, request.credentialId()) == null) {
            throw new BusinessException(400, "请选择本人有效的教务账号");
        }
        if (request.scopes().stream().anyMatch(scope -> !McpScope.supports(scope))) {
            throw new BusinessException(400, "MCP 权限选项无效");
        }
        long count = keys.selectCount(Wrappers.<McpApiKey>lambdaQuery().eq(McpApiKey::getUserId, userId));
        if (count >= MAX_KEYS) {
            throw new BusinessException(409, "已达到 10 把 MCP 密钥上限，请先销毁一把");
        }

        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String secret = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        var key = new McpApiKey();
        key.setUserId(userId);
        key.setJwCredentialId(request.credentialId());
        key.setName(request.name().trim());
        key.setKeyHash(hash(secret));
        key.setKeyPrefix(secret.substring(0, 16));
        key.setScopes(String.join(",", request.scopes().stream().distinct().toList()));
        key.setCreatedAt(LocalDateTime.now());
        keys.insert(key);
        return new CreatedKey(summary(key), secret);
    }

    public void delete(long userId, long id) {
        int removed = keys.delete(Wrappers.<McpApiKey>lambdaQuery()
                .eq(McpApiKey::getId, id).eq(McpApiKey::getUserId, userId));
        if (removed == 0) {
            throw new BusinessException(404, "密钥不存在");
        }
    }

    Principal authenticate(String header) {
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new BusinessException(401, "需要有效的 MCP 密钥");
        }
        String secret = header.substring(7);
        if (!KEY_FORMAT.matcher(secret).matches()) {
            throw new BusinessException(401, "需要有效的 MCP 密钥");
        }
        var key = keys.selectOne(Wrappers.<McpApiKey>lambdaQuery().eq(McpApiKey::getKeyHash, hash(secret)));
        if (key == null) {
            throw new BusinessException(401, "需要有效的 MCP 密钥");
        }
        requireAccess(key.getUserId());
        var binding = binding(key.getUserId(), key.getJwCredentialId());
        if (binding == null) {
            throw new BusinessException(403, "这把密钥绑定的教务账号已不可用");
        }
        return new Principal(key.getUserId(), binding.getJwAccount(), scopes(key));
    }

    private void requireAccess(long userId) {
        var user = users.selectById(userId);
        if (user == null || Integer.valueOf(0).equals(user.getStatus())) {
            throw new BusinessException(403, "当前账号无法使用 MCP");
        }
        access.requireFullAccess(userId);
    }

    private JwCredential binding(long userId, long id) {
        var binding = bindings.selectById(id);
        if (binding == null || !Objects.equals(binding.getUserId(), userId)
                || Integer.valueOf(0).equals(binding.getStatus())) {
            return null;
        }
        return binding;
    }

    private static byte[] hash(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static KeySummary summary(McpApiKey key) {
        return new KeySummary(key.getId(), key.getName(), key.getKeyPrefix(), key.getJwCredentialId(),
                scopes(key), key.getCreatedAt());
    }

    private static List<String> scopes(McpApiKey key) {
        return List.of(key.getScopes().split(","));
    }
}
