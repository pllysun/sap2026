package io.sapoj.service.judger;
import io.sapoj.common.BusinessException;
import io.sapoj.config.judger.JudgerProperties;
import io.sapoj.entity.judger.OjNode;
import io.sapoj.util.AesUtil;
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
                try (var channel=Files.newByteChannel(path, java.util.Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
                    channel.write(java.nio.ByteBuffer.wrap(HexFormat.of().formatHex(bytes).getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
                }
            }
            String value=Files.readString(path).strip();
            if (!value.matches("[0-9a-fA-F]{64}")) throw new IllegalStateException("节点密钥格式错误");
            return value;
        } catch(Exception e) { throw new BusinessException(503,"节点密钥存储不可用"); }
    }
    public String encrypt(String token) { return AesUtil.encrypt(key(),token); }
    public String token(OjNode node) {
        try { return Boolean.TRUE.equals(node.getBuiltin()) ? Files.readString(Path.of(config.getTokenFile())).strip() : AesUtil.decrypt(key(),node.getTokenCipher()); }
        catch(Exception e) { throw new BusinessException(503,"节点凭据不可用"); }
    }
}
