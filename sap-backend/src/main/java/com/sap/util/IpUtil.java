package com.sap.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 取客户端真实 IP。
 * <p><b>不可信首段陷阱</b>：nginx 用 {@code X-Forwarded-For $proxy_add_x_forwarded_for} 把真实对端
 * <i>追加</i>到客户端自带的 XFF 之后，故 XFF 首段是客户端可伪造的值——若按它限流/锁定，攻击者轮换该头即可绕过。
 * 因此优先采信 nginx 用 {@code X-Real-IP $remote_addr} <i>覆盖</i>设置的可信头；XFF 仅取「最后一跳」
 * （可信反代追加的真实对端），绝不取首段；都没有时退回 RemoteAddr（dev 直连场景即真实对端）。</p>
 */
public final class IpUtil {

    private IpUtil() {}

    public static String clientIp(HttpServletRequest request) {
        if (request == null) return "unknown";
        // nginx 覆盖设置，客户端无法伪造 → 最可信
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) return xri.trim();
        // 退化：取 XFF 最后一跳（可信反代追加的真实对端），而非可被客户端伪造的首段
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            return parts[parts.length - 1].trim();
        }
        String ip = request.getRemoteAddr();
        return ip != null ? ip : "unknown";
    }
}
