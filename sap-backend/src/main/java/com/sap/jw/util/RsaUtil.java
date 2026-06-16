package com.sap.jw.util;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA 工具：与前端 jsencrypt(PKCS#1 v1.5) 对齐。
 * <p>用于加密 CAS 登录密码——CAS 页面 jsencrypt 加密后输出 base64，此处等价实现。</p>
 */
public final class RsaUtil {

    private RsaUtil() {}

    /**
     * 用 PEM 格式公钥对明文做 RSA/PKCS1 加密，输出 base64（与 jsencrypt.encrypt 一致）。
     *
     * @param pem   PEM 公钥（含或不含 -----BEGIN/END----- 头尾均可）
     * @param plain 明文
     * @return base64 密文
     */
    public static String encryptByPublicKeyPem(String pem, String plain) {
        try {
            String body = pem.replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(body);
            PublicKey key = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(der));
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return Base64.getEncoder()
                    .encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("RSA 加密失败: " + e.getMessage(), e);
        }
    }
}
