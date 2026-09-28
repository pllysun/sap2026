package com.sap.util;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import java.util.function.Supplier;

/**
 * 取客户端真实 IP。
 * 仅当 TCP 对端是配置的可信代理时才读取转发头。默认信任本机 nginx；
 * 代理必须覆盖 X-Real-IP，或在 XFF 尾部追加真实对端。直连请求的代理头一律忽略。
 */
public final class IpUtil {

    private IpUtil() {}

    private static volatile Supplier<Set<String>> trustedProxies =
            () -> Set.of("127.0.0.1", "0:0:0:0:0:0:0:1");

    /** 仅接受确切 IP，不信任任意私网段或客户端提供的主机名。 */
    public static void configureTrustedProxies(Collection<String> addresses) {
        Set<String> normalized = normalizeProxies(addresses);
        trustedProxies = () -> normalized;
    }

    public static void useTrustedProxySource(Supplier<Set<String>> source) {
        trustedProxies = java.util.Objects.requireNonNull(source);
    }

    public static Set<String> parseTrustedProxies(String addresses) {
        return normalizeProxies(Arrays.asList(addresses.split("[,\\s]+")));
    }

    private static Set<String> normalizeProxies(Collection<String> addresses) {
        Set<String> normalized = new HashSet<>();
        for (String address : addresses) {
            if (address.isBlank()) continue;
            String ip = normalize(address);
            if (ip == null) throw new IllegalArgumentException("可信代理必须为有效的IPv4或IPv6地址");
            normalized.add(ip);
        }
        return Set.copyOf(normalized);
    }

    public static String clientIp(HttpServletRequest request) {
        if (request == null) return "unknown";
        String peer = normalize(request.getRemoteAddr());
        if (peer == null) return "unknown";
        if (!trustedProxies.get().contains(peer)) return peer;
        String xri = request.getHeader("X-Real-IP");
        String forwarded = normalize(xri);
        if (forwarded != null) return forwarded;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            forwarded = normalize(xff.substring(xff.lastIndexOf(',') + 1));
            if (forwarded != null) return forwarded;
        }
        return peer;
    }

    private static String normalize(String value) {
        if (value == null || value.length() > 64) return null;
        String ip = value.trim();
        if (ip.contains(":")) {
            // 字符白名单阻止 DNS 查询，同时排除 zone id、端口、列表等伪造值。
            if (!ip.matches("[0-9a-fA-F:.]+")) return null;
            try {
                return InetAddress.getByName(ip).getHostAddress();
            } catch (UnknownHostException ignored) {
                return null;
            }
        }
        if (!ip.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return null;
        String[] parts = ip.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            int octet = Integer.parseInt(parts[i]);
            if (octet > 255) return null;
            parts[i] = Integer.toString(octet);
        }
        return String.join(".", parts);
    }
}
