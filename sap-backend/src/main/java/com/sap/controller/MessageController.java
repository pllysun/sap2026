package com.sap.controller;

import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.entity.Message;
import com.sap.entity.MessageReply;
import com.sap.entity.MessageLike;
import com.sap.mapper.MessageMapper;
import com.sap.mapper.MessageReplyMapper;
import com.sap.mapper.MessageLikeMapper;
import com.sap.mapper.UserMapper;
import com.sap.entity.User;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/message")
public class MessageController {

    @Autowired
    private MessageMapper messageMapper;
    @Autowired
    private MessageReplyMapper messageReplyMapper;
    @Autowired
    private MessageLikeMapper messageLikeMapper;
    @Autowired
    private UserMapper userMapper;

    @GetMapping("/list")
    @OperationLog("查询留言列表")
    public Result<?> list(@RequestParam(defaultValue = "1") int current,
                          @RequestParam(defaultValue = "20") int size) {
        // 获取当前登录用户ID（可能未登录）
        Long currentUserId = null;
        try {
            currentUserId = StpUtil.getLoginIdAsLong();
        } catch (Exception e) {}

        Page<Message> page = messageMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<Message>().orderByDesc(Message::getCreatedAt)
        );

        List<Long> messageIds = page.getRecords().stream().map(Message::getId).collect(Collectors.toList());

        // 批量查询回复
        Map<Long, List<MessageReply>> replyMap = new HashMap<>();
        if (!messageIds.isEmpty()) {
            List<MessageReply> allReplies = messageReplyMapper.selectList(
                    new LambdaQueryWrapper<MessageReply>()
                            .in(MessageReply::getMessageId, messageIds)
                            .orderByAsc(MessageReply::getCreatedAt)
            );
            replyMap = allReplies.stream().collect(Collectors.groupingBy(MessageReply::getMessageId));
        }

        // 批量查询留言点赞数
        Map<Long, Long> msgLikeCountMap = new HashMap<>();
        if (!messageIds.isEmpty()) {
            for (Long msgId : messageIds) {
                long count = messageLikeMapper.selectCount(
                        new LambdaQueryWrapper<MessageLike>()
                                .eq(MessageLike::getTargetType, 0)
                                .eq(MessageLike::getTargetId, msgId)
                );
                msgLikeCountMap.put(msgId, count);
            }
        }

        // 当前用户已点赞的留言
        Set<Long> userLikedMsgIds = new HashSet<>();
        if (currentUserId != null && !messageIds.isEmpty()) {
            List<MessageLike> userMsgLikes = messageLikeMapper.selectList(
                    new LambdaQueryWrapper<MessageLike>()
                            .eq(MessageLike::getUserId, currentUserId)
                            .eq(MessageLike::getTargetType, 0)
                            .in(MessageLike::getTargetId, messageIds)
            );
            userLikedMsgIds = userMsgLikes.stream().map(MessageLike::getTargetId).collect(Collectors.toSet());
        }

        final Long finalCurrentUserId = currentUserId;
        final Set<Long> finalUserLikedMsgIds = userLikedMsgIds;
        final Map<Long, List<MessageReply>> finalReplyMap = replyMap;

        List<Map<String, Object>> records = page.getRecords().stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("content", m.getContent());
            map.put("createdAt", m.getCreatedAt());
            map.put("likeCount", msgLikeCountMap.getOrDefault(m.getId(), 0L));
            map.put("liked", finalUserLikedMsgIds.contains(m.getId()));

            if (m.getUserId() != null) {
                User user = userMapper.selectById(m.getUserId());
                map.put("userId", m.getUserId());
                map.put("userName", user != null ? user.getNickname() : "匿名");
                map.put("avatar", user != null ? user.getAvatar() : null);
            } else {
                map.put("userName", "匿名");
            }

            // 回复列表
            List<MessageReply> replies = finalReplyMap.getOrDefault(m.getId(), List.of());
            List<Map<String, Object>> replyList = replies.stream().map(r -> {
                Map<String, Object> rm = new HashMap<>();
                rm.put("id", r.getId());
                rm.put("content", r.getContent());
                rm.put("createdAt", r.getCreatedAt());
                User replyUser = userMapper.selectById(r.getUserId());
                rm.put("userId", r.getUserId());
                rm.put("userName", replyUser != null ? replyUser.getNickname() : "匿名");
                rm.put("avatar", replyUser != null ? replyUser.getAvatar() : null);

                // 回复点赞数
                long replyLikeCount = messageLikeMapper.selectCount(
                        new LambdaQueryWrapper<MessageLike>()
                                .eq(MessageLike::getTargetType, 1)
                                .eq(MessageLike::getTargetId, r.getId())
                );
                rm.put("likeCount", replyLikeCount);

                // 当前用户是否已点赞此回复
                boolean replyLiked = false;
                if (finalCurrentUserId != null) {
                    replyLiked = messageLikeMapper.selectCount(
                            new LambdaQueryWrapper<MessageLike>()
                                    .eq(MessageLike::getUserId, finalCurrentUserId)
                                    .eq(MessageLike::getTargetType, 1)
                                    .eq(MessageLike::getTargetId, r.getId())
                    ) > 0;
                }
                rm.put("liked", replyLiked);
                return rm;
            }).collect(Collectors.toList());
            map.put("replies", replyList);

            return map;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", page.getTotal());
        return Result.ok(result);
    }

    @PostMapping
    @OperationLog("发布留言")
    public Result<?> add(@RequestBody Map<String, String> params) {
        Message msg = new Message();
        msg.setContent(params.get("content"));
        try {
            msg.setUserId(StpUtil.getLoginIdAsLong());
        } catch (Exception e) {}
        messageMapper.insert(msg);
        return Result.ok("留言成功");
    }

    @PostMapping("/{id}/reply")
    @OperationLog("回复留言")
    public Result<?> reply(@PathVariable Long id, @RequestBody Map<String, String> params) {
        Message msg = messageMapper.selectById(id);
        if (msg == null) return Result.error("留言不存在");

        MessageReply reply = new MessageReply();
        reply.setMessageId(id);
        reply.setUserId(StpUtil.getLoginIdAsLong());
        reply.setContent(params.get("content"));
        messageReplyMapper.insert(reply);
        return Result.ok("回复成功");
    }

    @PostMapping("/like")
    @OperationLog("点赞")
    public Result<?> like(@RequestBody Map<String, Object> params) {
        Integer targetType = Integer.valueOf(params.get("targetType").toString());
        Long targetId = Long.valueOf(params.get("targetId").toString());
        Long userId = StpUtil.getLoginIdAsLong();

        // 检查是否已点赞
        Long exist = messageLikeMapper.selectCount(
                new LambdaQueryWrapper<MessageLike>()
                        .eq(MessageLike::getUserId, userId)
                        .eq(MessageLike::getTargetType, targetType)
                        .eq(MessageLike::getTargetId, targetId)
        );
        if (exist > 0) return Result.error("已点赞");

        MessageLike like = new MessageLike();
        like.setTargetType(targetType);
        like.setTargetId(targetId);
        like.setUserId(userId);
        messageLikeMapper.insert(like);
        return Result.ok("点赞成功");
    }

    @DeleteMapping("/like")
    @OperationLog("取消点赞")
    public Result<?> unlike(@RequestParam Integer targetType, @RequestParam Long targetId) {
        Long userId = StpUtil.getLoginIdAsLong();
        messageLikeMapper.delete(
                new LambdaQueryWrapper<MessageLike>()
                        .eq(MessageLike::getUserId, userId)
                        .eq(MessageLike::getTargetType, targetType)
                        .eq(MessageLike::getTargetId, targetId)
        );
        return Result.ok("取消点赞成功");
    }

    @DeleteMapping("/{id}")
    @OperationLog("删除留言")
    public Result<?> delete(@PathVariable Long id) {
        messageMapper.deleteById(id);
        return Result.ok("删除成功");
    }
}
