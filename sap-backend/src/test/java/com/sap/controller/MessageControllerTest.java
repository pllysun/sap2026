package com.sap.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sap.BaseUnitTest;
import com.sap.common.Result;
import com.sap.entity.Message;
import com.sap.entity.MessageLike;
import com.sap.entity.MessageReply;
import com.sap.entity.User;
import com.sap.mapper.MessageLikeMapper;
import com.sap.mapper.MessageMapper;
import com.sap.mapper.MessageReplyMapper;
import com.sap.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageControllerTest extends BaseUnitTest {

    @Mock MessageMapper messageMapper;
    @Mock MessageReplyMapper messageReplyMapper;
    @Mock MessageLikeMapper messageLikeMapper;
    @Mock UserMapper userMapper;

    @InjectMocks MessageController controller;

    private Message msg(long id, Long userId) {
        Message m = new Message();
        m.setId(id);
        m.setUserId(userId);
        m.setContent("内容" + id);
        m.setCreatedAt(LocalDateTime.now());
        return m;
    }

    private User user(long id, String nickname) {
        User u = new User();
        u.setId(id);
        u.setNickname(nickname);
        u.setAvatar("avatar" + id);
        return u;
    }

    // ============ list ============

    @Test
    @SuppressWarnings("unchecked")
    void list_loggedIn_aggregatesLikesAndReplies() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(99L);

            Message m = msg(1, 50L);
            when(messageMapper.selectPage(any(), any())).thenAnswer(inv -> {
                Page<Message> p = inv.getArgument(0);
                p.setRecords(List.of(m));
                p.setTotal(1);
                return p;
            });

            MessageReply reply = new MessageReply();
            reply.setId(10L);
            reply.setMessageId(1L);
            reply.setUserId(60L);
            reply.setContent("回复内容");
            reply.setCreatedAt(LocalDateTime.now());
            when(messageReplyMapper.selectList(any())).thenReturn(List.of(reply));

            // msg like count = 2 ; reply like count = 1 ; user reply-liked check >0
            when(messageLikeMapper.selectCount(any())).thenReturn(2L, 1L, 1L);

            MessageLike userLike = new MessageLike();
            userLike.setTargetId(1L);
            when(messageLikeMapper.selectList(any())).thenReturn(List.of(userLike));

            when(userMapper.selectById(50L)).thenReturn(user(50, "楼主"));
            when(userMapper.selectById(60L)).thenReturn(user(60, "回复人"));

            Result<?> r = controller.list(1, 20);
            assertEquals(200, r.getCode());
            Map<String, Object> data = (Map<String, Object>) r.getData();
            assertEquals(1L, data.get("total"));
            List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
            assertEquals(1, records.size());
            Map<String, Object> rec = records.get(0);
            assertEquals(2L, rec.get("likeCount"));
            assertEquals(true, rec.get("liked"));
            assertEquals("楼主", rec.get("userName"));
            List<Map<String, Object>> replies = (List<Map<String, Object>>) rec.get("replies");
            assertEquals(1, replies.size());
            assertEquals("回复人", replies.get(0).get("userName"));
            assertEquals(1L, replies.get(0).get("likeCount"));
            assertEquals(true, replies.get(0).get("liked"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_notLoggedIn_anonymousMessage() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenThrow(new RuntimeException("not logged in"));

            Message anon = msg(1, null); // anonymous (no userId)
            when(messageMapper.selectPage(any(), any())).thenAnswer(inv -> {
                Page<Message> p = inv.getArgument(0);
                p.setRecords(List.of(anon));
                p.setTotal(1);
                return p;
            });
            when(messageReplyMapper.selectList(any())).thenReturn(List.of());
            when(messageLikeMapper.selectCount(any())).thenReturn(0L);

            Result<?> r = controller.list(1, 20);
            Map<String, Object> data = (Map<String, Object>) r.getData();
            List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
            assertEquals("匿名", records.get(0).get("userName"));
            assertEquals(false, records.get(0).get("liked"));
            assertEquals(0L, records.get(0).get("likeCount"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_emptyPage_returnsEmptyRecords() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(99L);
            when(messageMapper.selectPage(any(), any())).thenAnswer(inv -> {
                Page<Message> p = inv.getArgument(0);
                p.setRecords(List.of());
                p.setTotal(0);
                return p;
            });
            Result<?> r = controller.list(1, 20);
            Map<String, Object> data = (Map<String, Object>) r.getData();
            assertEquals(0L, data.get("total"));
            assertTrue(((List<?>) data.get("records")).isEmpty());
        }
    }

    // ============ add ============

    @Test
    void add_loggedIn_setsUserId() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            Result<?> r = controller.add(Map.of("content", "你好"));
            verify(messageMapper).insert(any(Message.class));
            assertEquals("留言成功", r.getData());
        }
    }

    @Test
    void add_notLoggedIn_anonymousStillInserts() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenThrow(new RuntimeException("anon"));
            Result<?> r = controller.add(Map.of("content", "匿名留言"));
            verify(messageMapper).insert(any(Message.class));
            assertEquals("留言成功", r.getData());
        }
    }

    // ============ reply ============

    @Test
    void reply_messageNotFound_error() {
        when(messageMapper.selectById(1L)).thenReturn(null);
        Result<?> r = controller.reply(1L, Map.of("content", "x"));
        assertEquals(500, r.getCode());
        assertEquals("留言不存在", r.getMessage());
    }

    @Test
    void reply_inserts() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            when(messageMapper.selectById(1L)).thenReturn(msg(1, 50L));
            Result<?> r = controller.reply(1L, Map.of("content", "回复"));
            verify(messageReplyMapper).insert(any(MessageReply.class));
            assertEquals("回复成功", r.getData());
        }
    }

    // ============ like ============

    @Test
    void like_alreadyLiked_error() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            when(messageLikeMapper.selectCount(any())).thenReturn(1L);
            Result<?> r = controller.like(Map.of("targetType", 0, "targetId", 5));
            assertEquals("已点赞", r.getMessage());
            verify(messageLikeMapper, never()).insert(any());
        }
    }

    @Test
    void like_inserts() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            when(messageLikeMapper.selectCount(any())).thenReturn(0L);
            Result<?> r = controller.like(Map.of("targetType", 1, "targetId", 5));
            verify(messageLikeMapper).insert(any(MessageLike.class));
            assertEquals("点赞成功", r.getData());
        }
    }

    @Test
    void like_duplicateKey_treatedAsAlreadyLiked() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            when(messageLikeMapper.selectCount(any())).thenReturn(0L);
            doThrow(new DuplicateKeyException("dup")).when(messageLikeMapper).insert(any(MessageLike.class));
            Result<?> r = controller.like(Map.of("targetType", 0, "targetId", 5));
            assertEquals("已点赞", r.getMessage());
        }
    }

    // ============ unlike ============

    @Test
    void unlike_deletes() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            Result<?> r = controller.unlike(0, 5L);
            verify(messageLikeMapper).delete(any());
            assertEquals("取消点赞成功", r.getData());
        }
    }

    // ============ delete ============

    @Test
    void delete_messageNotFound_error() {
        when(messageMapper.selectById(1L)).thenReturn(null);
        Result<?> r = controller.delete(1L);
        assertEquals(500, r.getCode());
        assertEquals("留言不存在", r.getMessage());
    }

    @Test
    void delete_notOwnerNotAdmin_403() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            when(messageMapper.selectById(1L)).thenReturn(msg(1, 50L));
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L); // not owner
            st.when(StpUtil::getRoleList).thenReturn(List.of("3", "4")); // not admin
            Result<?> r = controller.delete(1L);
            assertEquals(403, r.getCode());
            assertEquals("无权删除该留言", r.getMessage());
            verify(messageMapper, never()).deleteById(anyLong());
        }
    }

    @Test
    void delete_owner_cascadesAndDeletes() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            when(messageMapper.selectById(1L)).thenReturn(msg(1, 7L));
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L); // owner
            st.when(StpUtil::getRoleList).thenReturn(List.of("3"));

            MessageReply reply = new MessageReply();
            reply.setId(10L);
            when(messageReplyMapper.selectList(any())).thenReturn(List.of(reply));

            Result<?> r = controller.delete(1L);
            assertEquals("删除成功", r.getData());
            verify(messageReplyMapper).delete(any());
            // both msg-like delete and reply-like delete (replyIds not empty)
            verify(messageLikeMapper, times(2)).delete(any());
            verify(messageMapper).deleteById(1L);
        }
    }

    @Test
    void delete_admin_noReplies_skipsReplyLikeDelete() {
        try (MockedStatic<StpUtil> st = mockStatic(StpUtil.class)) {
            when(messageMapper.selectById(1L)).thenReturn(msg(1, 50L));
            st.when(StpUtil::getLoginIdAsLong).thenReturn(7L); // not owner
            st.when(StpUtil::getRoleList).thenReturn(List.of("1")); // admin
            when(messageReplyMapper.selectList(any())).thenReturn(List.of());

            Result<?> r = controller.delete(1L);
            assertEquals("删除成功", r.getData());
            // only msg-like delete (no reply ids)
            verify(messageLikeMapper, times(1)).delete(any());
            verify(messageMapper).deleteById(1L);
        }
    }
}
