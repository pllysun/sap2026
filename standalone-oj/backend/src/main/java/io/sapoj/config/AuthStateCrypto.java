package io.sapoj.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;

/** 会话的可恢复数据加密保存；密钥独立于 MySQL，随 app-data 私有卷持久化。 */
@Component
public class AuthStateCrypto {
    @Value("${app.auth-state-key-file:./data/auth-state.key}") private String keyFile;
    private volatile SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();
    private synchronized SecretKeySpec key() {
        if (key != null) return key;
        try {
            Path path=Path.of(keyFile); Files.createDirectories(path.toAbsolutePath().getParent());
            if (!Files.exists(path)) {
                byte[] bytes=new byte[32];random.nextBytes(bytes);
                try (var out=Files.newByteChannel(path, Set.of(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE),
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
                    out.write(ByteBuffer.wrap(HexFormat.of().formatHex(bytes).getBytes(StandardCharsets.US_ASCII)));
                }
            }
            String value=Files.readString(path).strip();
            if (!value.matches("[0-9a-fA-F]{64}")) throw new IllegalStateException("Invalid authentication state key");
            key=new SecretKeySpec(HexFormat.of().parseHex(value),"AES");return key;
        } catch (Exception e) { throw new IllegalStateException("Private authentication state key unavailable",e); }
    }
    public String seal(String context,String value) {
        try {
            byte[] iv=new byte[12];random.nextBytes(iv);var cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key(),new GCMParameterSpec(128,iv));cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return "enc1:"+Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length+encrypted.length).put(iv).put(encrypted).array());
        } catch (Exception e) { throw new IllegalStateException("Authentication state encryption failed",e); }
    }
    public String open(String context,String value) {
        try {
            if (!value.startsWith("enc1:")) throw new IllegalArgumentException("Unencrypted authentication state");
            byte[] bytes=Base64.getDecoder().decode(value.substring(5));
            if (bytes.length < 28) throw new IllegalArgumentException("Invalid authentication state");
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,bytes,0,12));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(bytes,12,bytes.length-12),StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Authentication state integrity check failed",e); }
    }
}
