package com.sap.service.judger;
import com.sap.common.BusinessException;
import com.sap.config.judger.JudgerProperties;
import com.sap.entity.judger.OjNode;
import com.sap.jw.util.AesUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.HexFormat;

@Service @RequiredArgsConstructor
public class JudgerNodeSecrets {
    private final JudgerProperties config;
    private synchronized String key() {
        try {
            Path path=Path.of(config.getNodeKeyFile());
            if (!Files.exists(path)) {
                Files.createDirectories(path.toAbsolutePath().getParent());
                byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
                Files.writeString(path,HexFormat.of().formatHex(bytes),StandardOpenOption.CREATE_NEW);
                Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rw-------"));
            }
            return Files.readString(path).strip();
        } catch(Exception e) { throw new BusinessException(503,"节点密钥存储不可用"); }
    }
    public String encrypt(String token) { return AesUtil.encrypt(key(),token); }
    public String token(OjNode node) {
        try { return Boolean.TRUE.equals(node.getBuiltin()) ? Files.readString(Path.of(config.getTokenFile())).strip() : AesUtil.decrypt(key(),node.getTokenCipher()); }
        catch(Exception e) { throw new BusinessException(503,"节点凭据不可用"); }
    }
}
