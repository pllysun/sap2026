package com.sap.jw.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sap.jw.entity.JwCredential;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface JwCredentialMapper extends BaseMapper<JwCredential> {

    /**
     * 物理删除某会员名下某学号的绑定（原生 SQL，绕过 {@code @TableLogic} 逻辑删除）。
     * 解绑须真正删除密文凭据（隐私合规），且不留逻辑删除行占用唯一索引 uk_user_account。
     */
    @Delete("DELETE FROM jw_credential WHERE user_id = #{userId} AND jw_account = #{account}")
    int physicalDelete(@Param("userId") Long userId, @Param("account") String account);

    /**
     * 复活/更新某学号绑定（原生 SQL，命中含逻辑删除的行）：重置密文、状态正常、deleted=0、刷新更新时间。
     * 因 uk_user_account 不含 deleted，同一 (user_id, jw_account) 物理上至多一行，故至多影响一行。
     * @return 受影响行数（0 = 库里没有任何物理行，调用方需走 insert）
     */
    @Update("UPDATE jw_credential SET jw_password_enc = #{enc}, status = 1, deleted = 0, updated_at = NOW() " +
            "WHERE user_id = #{userId} AND jw_account = #{account}")
    int reviveOrUpdate(@Param("userId") Long userId, @Param("account") String account, @Param("enc") String enc);
}
