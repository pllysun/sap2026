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

    /**
     * 新增或更新某个学号的绑定（密码加密存储）。
     * <p>用原生 UPDATE 先尝试复活/更新：能命中含“逻辑删除”的历史行（{@link #get} 带 deleted=0 过滤查不到它），
     * 0 行才插入。这样既修复了“以前解绑残留的软删行占着唯一索引 uk_user_account → 重绑报‘数据已存在’”的问题，
     * 也避免再次踩坑。</p>
     */
    public void save(Long userId, String account, String rawPassword) {
        String enc = AesUtil.encrypt(props.getAesKey(), rawPassword);
        int affected = mapper.reviveOrUpdate(userId, account, enc);
        if (affected == 0) {
            JwCredential c = new JwCredential();
            c.setUserId(userId);
            c.setJwAccount(account);
            c.setJwPasswordEnc(enc);
            c.setStatus(1);
            mapper.insert(c);
        }
    }

    /** 取解密后的学校密码（仅供登录链路内部使用）。 */
    public String getDecryptedPassword(Long userId, String account) {
        return AesUtil.decrypt(props.getAesKey(), required(userId, account).getJwPasswordEnc());
    }

    /**
     * 解绑：物理删除该学号的密文凭据。
     * <p>不走逻辑删除——一是隐私合规（解绑须真正从服务器删除学校密码），
     * 二是软删行会占着唯一索引 uk_user_account 导致同一学号无法重新绑定。</p>
     */
    public void unbind(Long userId, String account) {
        if (account == null || account.isBlank()) return;
        mapper.physicalDelete(userId, account.trim());
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
