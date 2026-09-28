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
                new Page<>(Math.max(1, current), Math.max(1, Math.min(size, 50))),
                new LambdaQueryWrapper<Message>().orderByDesc(Message::getCreatedAt).orderByDesc(Message::getId)
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
                map.put("userName", displayName(user));
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
                User replyUser = r.getUserId() == null ? null : userMapper.selectById(r.getUserId());
                rm.put("userId", r.getUserId());
                rm.put("userName", r.getUserId() == null ? "匿名" : displayName(replyUser));
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
        Integer targetType;
        Long targetId;
        try {
            targetType = Integer.valueOf(String.valueOf(params.get("targetType")));
            targetId = Long.valueOf(String.valueOf(params.get("targetId")));
        } catch (NumberFormatException e) {
            return Result.error(400, "点赞参数不正确");
        }
        Result<?> invalid = validateTarget(targetType, targetId);
        if (invalid != null) return invalid;
        Long userId = StpUtil.getLoginIdAsLong();

        // 检查是否已点赞
        Long exist = messageLikeMapper.selectCount(
                new LambdaQueryWrapper<MessageLike>()
                        .eq(MessageLike::getUserId, userId)
                        .eq(MessageLike::getTargetType, targetType)
                        .eq(MessageLike::getTargetId, targetId)
        );
        if (exist > 0) return likeState(userId, targetType, targetId);

        MessageLike like = new MessageLike();
        like.setTargetType(targetType);
        like.setTargetId(targetId);
        like.setUserId(userId);
        try {
            messageLikeMapper.insert(like);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // POST 是“设为已点赞”，重复请求仍返回真实状态，而不是静默失败。
        }
        return likeState(userId, targetType, targetId);
    }

    @DeleteMapping("/like")
    @OperationLog("取消点赞")
    public Result<?> unlike(@RequestParam Integer targetType, @RequestParam Long targetId) {
        Result<?> invalid = validateTarget(targetType, targetId);
        if (invalid != null) return invalid;
        Long userId = StpUtil.getLoginIdAsLong();
        messageLikeMapper.delete(
                new LambdaQueryWrapper<MessageLike>()
                        .eq(MessageLike::getUserId, userId)
                        .eq(MessageLike::getTargetType, targetType)
                        .eq(MessageLike::getTargetId, targetId)
        );
        return likeState(userId, targetType, targetId);
    }

    static String displayName(User user) {
        if (user == null) return "已注销用户";
        if (user.getNickname() != null && !user.getNickname().isBlank()) return user.getNickname().strip();
        if (user.getName() != null && !user.getName().isBlank()) return user.getName().strip();
        return "软协同学";
    }

    private Result<?> validateTarget(Integer type, Long id) {
        if (type == null || (type != 0 && type != 1) || id == null || id <= 0)
            return Result.error(400, "点赞参数不正确");
        if (type == 0 ? messageMapper.selectById(id) == null : messageReplyMapper.selectById(id) == null)
            return Result.error(404, "留言或回复已不存在，请刷新页面");
        return null;
    }

    private Result<?> likeState(Long userId, Integer type, Long id) {
        long count = messageLikeMapper.selectCount(new LambdaQueryWrapper<MessageLike>()
                .eq(MessageLike::getTargetType, type).eq(MessageLike::getTargetId, id));
        boolean liked = messageLikeMapper.selectCount(new LambdaQueryWrapper<MessageLike>()
                .eq(MessageLike::getTargetType, type).eq(MessageLike::getTargetId, id)
                .eq(MessageLike::getUserId, userId)) > 0;
        return Result.ok(Map.of("liked", liked, "likeCount", count));
    }

    @DeleteMapping("/{id}")
    @OperationLog("删除留言")
    public Result<?> delete(@PathVariable Long id) {
        Message msg = messageMapper.selectById(id);
        if (msg == null) return Result.error("留言不存在");
        Long userId = StpUtil.getLoginIdAsLong();
        List<String> roles = StpUtil.getRoleList();
        boolean isAdmin = roles.stream().anyMatch(r -> "0".equals(r) || "1".equals(r) || "2".equals(r));
        boolean isOwner = msg.getUserId() != null && msg.getUserId().equals(userId);
        if (!isAdmin && !isOwner) {
            return Result.error(403, "无权删除该留言");
        }
        // 级联清理：回复、留言点赞、回复点赞，避免孤儿数据污染统计
        List<MessageReply> replies = messageReplyMapper.selectList(
                new LambdaQueryWrapper<MessageReply>().eq(MessageReply::getMessageId, id));
        List<Long> replyIds = replies.stream().map(MessageReply::getId).collect(Collectors.toList());
        messageReplyMapper.delete(new LambdaQueryWrapper<MessageReply>().eq(MessageReply::getMessageId, id));
        messageLikeMapper.delete(new LambdaQueryWrapper<MessageLike>()
                .eq(MessageLike::getTargetType, 0).eq(MessageLike::getTargetId, id));
        if (!replyIds.isEmpty()) {
            messageLikeMapper.delete(new LambdaQueryWrapper<MessageLike>()
                    .eq(MessageLike::getTargetType, 1).in(MessageLike::getTargetId, replyIds));
        }
        messageMapper.deleteById(id);
        return Result.ok("删除成功");
    }
}
