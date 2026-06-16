package com.sap.jw.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sap.common.BusinessException;
import com.sap.jw.config.JwProperties;
import com.sap.jw.entity.JwCredential;
import com.sap.jw.mapper.JwCredentialMapper;
import com.sap.jw.util.AesUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 教务账号绑定的持久化：密码 AES 加密后落库。
 * <p>一个会员可绑定多个教务学号，按 (userId, account) 维度操作。</p>
 */
@Service
public class JwCredentialService {

    private final JwCredentialMapper mapper;
    private final JwProperties props;

    public JwCredentialService(JwCredentialMapper mapper, JwProperties props) {
        this.mapper = mapper;
        this.props = props;
    }

    /** 会员名下全部绑定（按绑定先后） */
    public List<JwCredential> listByUser(Long userId) {
        return mapper.selectList(
                new QueryWrapper<JwCredential>().eq("user_id", userId).orderByAsc("id"));
    }

    public JwCredential get(Long userId, String account) {
        return mapper.selectOne(
                new QueryWrapper<JwCredential>().eq("user_id", userId).eq("jw_account", account));
    }

    public boolean isBound(Long userId) {
        return !listByUser(userId).isEmpty();
    }

    /** 默认账号 = 最早绑定的；无则 null。 */
    public String defaultAccount(Long userId) {
        List<JwCredential> list = listByUser(userId);
        return list.isEmpty() ? null : list.get(0).getJwAccount();
    }

    /** 新增或更新某个学号的绑定（密码加密存储）。 */
    public void save(Long userId, String account, String rawPassword) {
        String enc = AesUtil.encrypt(props.getAesKey(), rawPassword);
        JwCredential existing = get(userId, account);
        if (existing == null) {
            JwCredential c = new JwCredential();
            c.setUserId(userId);
            c.setJwAccount(account);
            c.setJwPasswordEnc(enc);
            c.setStatus(1);
            mapper.insert(c);
        } else {
            existing.setJwPasswordEnc(enc);
            existing.setStatus(1);
            mapper.updateById(existing);
        }
    }

    /** 取解密后的学校密码（仅供登录链路内部使用）。 */
    public String getDecryptedPassword(Long userId, String account) {
        return AesUtil.decrypt(props.getAesKey(), required(userId, account).getJwPasswordEnc());
    }

    public void unbind(Long userId, String account) {
        JwCredential c = get(userId, account);
        if (c != null) mapper.deleteById(c.getId());
    }

    public void markSynced(Long userId, String account) {
        JwCredential c = get(userId, account);
        if (c != null) {
            c.setLastSyncAt(LocalDateTime.now());
            mapper.updateById(c);
        }
    }

    private JwCredential required(Long userId, String account) {
        if (account == null || account.isBlank()) throw new BusinessException("未指定教务账号");
        JwCredential c = get(userId, account);
        if (c == null) throw new BusinessException("尚未绑定该教务账号");
        return c;
    }
}
