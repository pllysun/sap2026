package com.sap.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.sap.entity.Message;
import com.sap.entity.MessageReply;
import com.sap.mapper.MessageMapper;
import com.sap.mapper.MessageReplyMapper;
import com.sap.mapper.MessageLikeMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageLikePersistenceTest {
    @Test void repeatedLikeUnlikeRelikeAndReplyRemainConsistent() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        var db = new JdbcTemplate(ds);
        db.execute("CREATE TABLE msg_like(id BIGINT AUTO_INCREMENT PRIMARY KEY, target_type INT, target_id BIGINT, user_id BIGINT, created_at TIMESTAMP, CONSTRAINT uk_msg_like UNIQUE(user_id,target_type,target_id))");
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds);
        var sessions = factory.getObject(); sessions.getConfiguration().addMapper(MessageLikeMapper.class);
        var likes = new SqlSessionTemplate(sessions).getMapper(MessageLikeMapper.class);
        var messages = mock(MessageMapper.class); var replies = mock(MessageReplyMapper.class);
        when(messages.selectById(5L)).thenReturn(new Message()); when(replies.selectById(5L)).thenReturn(new MessageReply());
        var controller = new MessageController();
        ReflectionTestUtils.setField(controller, "messageMapper", messages);
        ReflectionTestUtils.setField(controller, "messageReplyMapper", replies);
        ReflectionTestUtils.setField(controller, "messageLikeMapper", likes);
        try (var st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            var target = Map.<String,Object>of("targetType", 0, "targetId", "5");
            for (int i = 0; i < 3; i++) {
                assertEquals(Map.of("liked", true, "likeCount", 1L), controller.like(target).getData());
                assertEquals(Map.of("liked", true, "likeCount", 1L), controller.like(target).getData());
                assertEquals(Map.of("liked", false, "likeCount", 0L), controller.unlike(0, 5L).getData());
                assertEquals(Map.of("liked", false, "likeCount", 0L), controller.unlike(0, 5L).getData());
            }
            controller.like(target);
            st.when(StpUtil::getLoginIdAsLong).thenReturn(8L);
            assertEquals(Map.of("liked", true, "likeCount", 2L), controller.like(target).getData());
            assertEquals(Map.of("liked", true, "likeCount", 1L), controller.like(Map.of("targetType", 1, "targetId", 5)).getData());
            assertEquals(Map.of("liked", false, "likeCount", 1L), controller.unlike(0, 5L).getData());
            assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM msg_like", Integer.class));
        } finally { db.execute("SHUTDOWN"); }
    }
}
