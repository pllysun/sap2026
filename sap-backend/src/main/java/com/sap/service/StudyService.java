package com.sap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sap.common.BusinessException;
import com.sap.entity.*;
import com.sap.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class StudyService {

    @Autowired private StudyActivityMapper studyActivityMapper;
    @Autowired private StudyLeaderMapper studyLeaderMapper;
    @Autowired private StudyMemberMapper studyMemberMapper;
    @Autowired private StudyScoreMapper studyScoreMapper;
    @Autowired private StudyMaterialMapper studyMaterialMapper;
    @Autowired private TermMapper termMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private SettingMapper settingMapper;
    @Autowired private PositionMapper positionMapper;
    @Autowired private CacheService cacheService;

    // ============= 学习活动 =============

    public List<StudyActivity> listActivities(String grade) {
        LambdaQueryWrapper<StudyActivity> wrapper = new LambdaQueryWrapper<>();
        if (grade != null && !grade.isEmpty()) {
            wrapper.eq(StudyActivity::getGrade, grade);
        }
        wrapper.orderByDesc(StudyActivity::getSeqNum);
        return studyActivityMapper.selectList(wrapper);
    }

    /**
     * 获取学习活动表中所有不重复的年份（降序）
     */
    public List<String> getStudyActivityYears() {
        List<StudyActivity> all = studyActivityMapper.selectList(
                new LambdaQueryWrapper<StudyActivity>().select(StudyActivity::getGrade).groupBy(StudyActivity::getGrade)
        );
        return all.stream().map(StudyActivity::getGrade).sorted(Comparator.reverseOrder()).collect(Collectors.toList());
    }

    /**
     * 聚合查询：一次性返回所有活动+成员数（按年级降序、seqNum降序）
     * 用于用户端成绩统计页面，避免 N+1 查询
     */
    public List<Map<String, Object>> listAllActivitiesWithStats() {
        // 1. 一次查询所有活动
        List<StudyActivity> allActs = studyActivityMapper.selectList(
                new LambdaQueryWrapper<StudyActivity>().orderByDesc(StudyActivity::getGrade, StudyActivity::getSeqNum)
        );
        if (allActs.isEmpty()) return Collections.emptyList();

        // 2. 批量查询每个活动最新周期的成员数
        Map<Long, Long> memberCountMap = new HashMap<>();
        for (StudyActivity act : allActs) {
            Long count = studyMemberMapper.selectCount(
                    new LambdaQueryWrapper<StudyMember>()
                            .eq(StudyMember::getActivityId, act.getId())
                            .eq(StudyMember::getWeek, act.getCurrentWeek())
            );
            memberCountMap.put(act.getId(), count);
        }

        // 3. 组装结果
        List<Map<String, Object>> result = new ArrayList<>();
        for (StudyActivity act : allActs) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", act.getId());
            item.put("title", act.getTitle());
            item.put("grade", act.getGrade());
            item.put("seqNum", act.getSeqNum());
            item.put("currentWeek", act.getCurrentWeek());
            item.put("activeWeek", act.getActiveWeek());
            item.put("status", act.getStatus());
            item.put("memberCount", memberCountMap.getOrDefault(act.getId(), 0L));
            result.add(item);
        }
        return result;
    }

    @Transactional
    public void createActivity(StudyActivity activity) {
        Setting gradeSetting = settingMapper.selectOne(
                new LambdaQueryWrapper<Setting>().eq(Setting::getSettingKey, "current_grade")
        );
        String currentGrade = gradeSetting != null ? gradeSetting.getSettingValue() : "2025";
        activity.setGrade(currentGrade);

        LambdaQueryWrapper<StudyActivity> cntWrapper = new LambdaQueryWrapper<>();
        cntWrapper.eq(StudyActivity::getGrade, currentGrade);
        long count = studyActivityMapper.selectCount(cntWrapper);
        activity.setSeqNum((int) count + 1);
        activity.setCurrentWeek(1);
        activity.setActiveWeek(1);
        activity.setStatus(1);

        // 归档同年级所有进行中的活动
        List<StudyActivity> activeOnes = studyActivityMapper.selectList(
                new LambdaQueryWrapper<StudyActivity>()
                        .eq(StudyActivity::getGrade, currentGrade)
                        .eq(StudyActivity::getStatus, 1)
        );
        for (StudyActivity a : activeOnes) {
            a.setStatus(0);
            studyActivityMapper.updateById(a);
        }

        studyActivityMapper.insert(activity);

        // 自动从换届表添加当前年级管理层为负责人
        autoAddLeadersFromTerm(activity);
    }

    /**
     * 从换届表中获取当前年级的管理层（排除普通成员），自动添加为负责人。
     * 通过 Position 表的 roleCode 区分：roleCode <= 2 为管理层（会长/管理员）
     */
    private void autoAddLeadersFromTerm(StudyActivity activity) {
        // 1. 从缓存获取管理层身份
        List<Position> adminPositions = cacheService.getAdminPositions();
        if (adminPositions.isEmpty()) return;
        List<Integer> adminPosIds = adminPositions.stream()
                .map(Position::getId).collect(Collectors.toList());

        // 2. 从换届表中筛选当前年级 + 管理层身份的人
        List<Term> terms = termMapper.selectList(
                new LambdaQueryWrapper<Term>()
                        .eq(Term::getGrade, activity.getGrade())
                        .in(Term::getPositionId, adminPosIds)
        );

        // 3. 去重后添加为负责人（从缓存获取用户信息）
        Set<Long> addedUsers = new HashSet<>();
        for (Term term : terms) {
            if (addedUsers.contains(term.getUserId())) continue;
            User user = cacheService.getUserById(term.getUserId());
            if (user != null) {
                StudyLeader leader = new StudyLeader();
                leader.setActivityId(activity.getId());
                leader.setUserId(user.getId());
                leader.setStudentId(user.getStudentId());
                studyLeaderMapper.insert(leader);
                addedUsers.add(term.getUserId());
            }
        }
    }

    /**
     * 获取活动详情 + 所有周期汇总数据
     */
    public Map<String, Object> getActivityDetail(Long activityId) {
        StudyActivity activity = studyActivityMapper.selectById(activityId);
        if (activity == null) throw new BusinessException("活动不存在");

        Map<String, Object> detail = new HashMap<>();
        detail.put("activity", activity);

        // 负责人列表
        detail.put("leaders", listLeaders(activityId));

        // 当前周期总成员数
        long totalMembers = studyMemberMapper.selectCount(
                new LambdaQueryWrapper<StudyMember>()
                        .eq(StudyMember::getActivityId, activityId)
                        .eq(StudyMember::getWeek, activity.getCurrentWeek())
        );
        detail.put("totalMembers", totalMembers);

        // 每周期汇总
        List<Map<String, Object>> weekSummaries = new ArrayList<>();
        for (int w = 1; w <= activity.getCurrentWeek(); w++) {
            Map<String, Object> ws = new HashMap<>();
            ws.put("week", w);

            long memberCount = studyMemberMapper.selectCount(
                    new LambdaQueryWrapper<StudyMember>()
                            .eq(StudyMember::getActivityId, activityId)
                            .eq(StudyMember::getWeek, w)
            );
            ws.put("memberCount", memberCount);

            List<StudyScore> weekScores = studyScoreMapper.selectList(
                    new LambdaQueryWrapper<StudyScore>()
                            .eq(StudyScore::getActivityId, activityId)
                            .eq(StudyScore::getWeek, w)
            );
            ws.put("scoredCount", weekScores.size());
            double avgScore = weekScores.stream().mapToInt(StudyScore::getScore).average().orElse(0);
            ws.put("avgScore", Math.round(avgScore * 10) / 10.0);

            // 作业题目
            StudyMaterial homework = studyMaterialMapper.selectOne(
                    new LambdaQueryWrapper<StudyMaterial>()
                            .eq(StudyMaterial::getActivityId, activityId)
                            .eq(StudyMaterial::getWeek, w)
                            .eq(StudyMaterial::getFileType, 1)
            );
            ws.put("homeworkTitle", homework != null ? homework.getTitle() : null);
            ws.put("homeworkFileUrl", homework != null ? homework.getFileUrl() : null);
            ws.put("homeworkFileName", homework != null ? homework.getFileName() : null);

            weekSummaries.add(ws);
        }
        detail.put("weekSummaries", weekSummaries);

        return detail;
    }

    /**
     * 获取单个周期完整数据
     */
    public List<Map<String, Object>> getCycleDetail(Long activityId, Integer week) {
        List<StudyMember> members = studyMemberMapper.selectList(
                new LambdaQueryWrapper<StudyMember>()
                        .eq(StudyMember::getActivityId, activityId)
                        .eq(StudyMember::getWeek, week)
        );
        if (members.isEmpty()) return List.of();

        // 负责人查询(仍需数据库，但无N+1)
        List<Long> leaderIds = members.stream().map(StudyMember::getLeaderId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, StudyLeader> leaderMap = new HashMap<>();
        if (!leaderIds.isEmpty()) {
            List<StudyLeader> leaders = studyLeaderMapper.selectBatchIds(leaderIds);
            leaderMap = leaders.stream().collect(Collectors.toMap(StudyLeader::getId, l -> l));
        }

        // 批量预加载：评分
        List<StudyScore> allScores = studyScoreMapper.selectList(
                new LambdaQueryWrapper<StudyScore>()
                        .eq(StudyScore::getActivityId, activityId)
                        .eq(StudyScore::getWeek, week)
        );
        Map<Long, StudyScore> scoreMap = allScores.stream()
                .collect(Collectors.toMap(StudyScore::getMemberUserId, s -> s, (a, b) -> a));

        // 批量预加载：作业提交
        List<StudyMaterial> allMaterials = studyMaterialMapper.selectList(
                new LambdaQueryWrapper<StudyMaterial>()
                        .eq(StudyMaterial::getActivityId, activityId)
                        .eq(StudyMaterial::getWeek, week)
                        .eq(StudyMaterial::getFileType, 2)
        );
        Map<Long, List<StudyMaterial>> materialMap = allMaterials.stream()
                .collect(Collectors.groupingBy(StudyMaterial::getUserId));

        // 组装结果（用户信息从缓存获取，无额外查询）
        Map<Long, StudyLeader> finalLeaderMap = leaderMap;
        return members.stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("userId", m.getUserId());
            map.put("leaderId", m.getLeaderId());
            map.put("week", m.getWeek());

            map.put("userName", cacheService.getUserName(m.getUserId()));
            map.put("studentId", cacheService.getStudentId(m.getUserId()));

            if (m.getLeaderId() != null) {
                StudyLeader leader = finalLeaderMap.get(m.getLeaderId());
                if (leader != null) {
                    map.put("leaderName", cacheService.getUserName(leader.getUserId()));
                }
            }
            if (!map.containsKey("leaderName")) map.put("leaderName", "未分配");

            StudyScore score = scoreMap.get(m.getUserId());
            map.put("score", score != null ? score.getScore() : null);
            map.put("comment", score != null ? score.getComment() : null);
            map.put("scoreId", score != null ? score.getId() : null);

            List<StudyMaterial> subs = materialMap.getOrDefault(m.getUserId(), List.of());
            map.put("submitted", !subs.isEmpty());
            map.put("materials", subs);

            return map;
        }).collect(Collectors.toList());
    }

    // ============= 负责人管理 =============

    public List<Map<String, Object>> listLeaders(Long activityId) {
        StudyActivity activity = studyActivityMapper.selectById(activityId);
        int currentWeek = activity != null ? activity.getCurrentWeek() : 1;

        List<StudyLeader> leaders = studyLeaderMapper.selectList(
                new LambdaQueryWrapper<StudyLeader>().eq(StudyLeader::getActivityId, activityId)
        );
        if (leaders.isEmpty()) return List.of();

        // 只统计当前周期的成员数
        List<Long> leaderIds = leaders.stream().map(StudyLeader::getId).collect(Collectors.toList());
        List<StudyMember> allMembers = studyMemberMapper.selectList(
                new LambdaQueryWrapper<StudyMember>()
                        .eq(StudyMember::getActivityId, activityId)
                        .eq(StudyMember::getWeek, currentWeek)
                        .in(StudyMember::getLeaderId, leaderIds)
        );
        Map<Long, Long> memberCountMap = allMembers.stream()
                .collect(Collectors.groupingBy(StudyMember::getLeaderId, Collectors.counting()));

        return leaders.stream().map(l -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", l.getId());
            map.put("userId", l.getUserId());
            map.put("studentId", l.getStudentId());
            map.put("deleted", l.getDeleted());
            map.put("userName", cacheService.getUserName(l.getUserId()));
            map.put("memberCount", memberCountMap.getOrDefault(l.getId(), 0L));
            return map;
        }).collect(Collectors.toList());
    }

    public void addLeader(StudyLeader leader) {
        User user = cacheService.getUserByStudentId(leader.getStudentId());
        if (user == null) throw new BusinessException("该学号用户不存在");
        // 检查是否已存在
        Long exist = studyLeaderMapper.selectCount(
                new LambdaQueryWrapper<StudyLeader>()
                        .eq(StudyLeader::getActivityId, leader.getActivityId())
                        .eq(StudyLeader::getUserId, user.getId())
        );
        if (exist > 0) throw new BusinessException("该用户已是负责人");
        leader.setUserId(user.getId());
        studyLeaderMapper.insert(leader);
    }

    @Transactional
    public void deleteLeader(Long id) {
        StudyLeader leader = studyLeaderMapper.selectById(id);
        if (leader == null) throw new BusinessException("负责人不存在");
        studyLeaderMapper.deleteById(id);

        // 将该负责人的成员重新均分
        StudyActivity activity = studyActivityMapper.selectById(leader.getActivityId());
        if (activity != null) {
            List<StudyMember> orphaned = studyMemberMapper.selectList(
                    new LambdaQueryWrapper<StudyMember>()
                            .eq(StudyMember::getLeaderId, id)
                            .eq(StudyMember::getActivityId, leader.getActivityId())
                            .eq(StudyMember::getWeek, activity.getCurrentWeek())
            );
            for (StudyMember m : orphaned) {
                m.setLeaderId(null);
                studyMemberMapper.updateById(m);
                assignToLeastLoadedLeader(m);
            }
        }
    }

    public void restoreLeader(Long id) {
        StudyLeader leader = new StudyLeader();
        leader.setId(id);
        leader.setDeleted(0);
        studyLeaderMapper.updateById(leader);
    }

    // ============= 成员管理 =============

    /**
     * 自动加入当前年级最新进行中的学习活动
     * @param userId 用户ID
     */
    @Transactional
    public void autoJoinLatest(Long userId) {
        Setting gradeSetting = settingMapper.selectOne(
                new LambdaQueryWrapper<Setting>().eq(Setting::getSettingKey, "current_grade")
        );
        String currentGrade = gradeSetting != null ? gradeSetting.getSettingValue() : "2025";

        StudyActivity latest = studyActivityMapper.selectOne(
                new LambdaQueryWrapper<StudyActivity>()
                        .eq(StudyActivity::getGrade, currentGrade)
                        .eq(StudyActivity::getStatus, 1)
                        .orderByDesc(StudyActivity::getSeqNum)
                        .last("LIMIT 1")
        );
        if (latest == null) throw new BusinessException("当前年级没有进行中的学习活动");
        memberJoin(latest.getId(), userId);
    }

    @Transactional
    public void memberJoin(Long activityId, Long userId) {
        StudyActivity activity = studyActivityMapper.selectById(activityId);
        if (activity == null) throw new BusinessException("学习活动不存在");

        LambdaQueryWrapper<StudyMember> check = new LambdaQueryWrapper<>();
        check.eq(StudyMember::getActivityId, activityId)
                .eq(StudyMember::getUserId, userId)
                .eq(StudyMember::getWeek, activity.getCurrentWeek());
        if (studyMemberMapper.selectCount(check) > 0) {
            throw new BusinessException("已加入该活动当前周期");
        }

        StudyMember member = new StudyMember();
        member.setActivityId(activityId);
        member.setUserId(userId);
        member.setWeek(activity.getCurrentWeek());
        studyMemberMapper.insert(member);
        assignToLeastLoadedLeader(member);
    }

    /**
     * 批量添加成员
     */
    @Transactional
    public int batchMemberJoin(Long activityId, List<Long> userIds) {
        int success = 0;
        for (Long uid : userIds) {
            try {
                memberJoin(activityId, uid);
                success++;
            } catch (BusinessException e) {
                // 已加入跳过
            }
        }
        return success;
    }

    private void assignToLeastLoadedLeader(StudyMember member) {
        List<StudyLeader> leaders = studyLeaderMapper.selectList(
                new LambdaQueryWrapper<StudyLeader>()
                        .eq(StudyLeader::getActivityId, member.getActivityId())
        );
        if (leaders.isEmpty()) return;

        long minCount = Long.MAX_VALUE;
        Long bestLeaderId = null;
        for (StudyLeader leader : leaders) {
            long count = studyMemberMapper.selectCount(
                    new LambdaQueryWrapper<StudyMember>()
                            .eq(StudyMember::getLeaderId, leader.getId())
                            .eq(StudyMember::getActivityId, member.getActivityId())
                            .eq(StudyMember::getWeek, member.getWeek())
            );
            if (count < minCount) {
                minCount = count;
                bestLeaderId = leader.getId();
            }
        }
        if (bestLeaderId != null) {
            member.setLeaderId(bestLeaderId);
            studyMemberMapper.updateById(member);
        }
    }

    public void reassignMember(Long memberId, Long leaderId) {
        StudyMember member = studyMemberMapper.selectById(memberId);
        if (member == null) throw new BusinessException("成员不存在");
        member.setLeaderId(leaderId);
        studyMemberMapper.updateById(member);
    }

    // ============= 周期管理 =============

    /**
     * 进入下一周期（随机均分成员）
     */
    @Transactional
    public void nextWeek(Long activityId) {
        StudyActivity activity = studyActivityMapper.selectById(activityId);
        if (activity == null) throw new BusinessException("活动不存在");

        int newWeek = activity.getCurrentWeek() + 1;
        activity.setCurrentWeek(newWeek);
        activity.setActiveWeek(newWeek);
        studyActivityMapper.updateById(activity);

        // 获取上一周期所有成员
        List<StudyMember> prevMembers = studyMemberMapper.selectList(
                new LambdaQueryWrapper<StudyMember>()
                        .eq(StudyMember::getActivityId, activityId)
                        .eq(StudyMember::getWeek, newWeek - 1)
        );
        List<StudyLeader> leaders = studyLeaderMapper.selectList(
                new LambdaQueryWrapper<StudyLeader>()
                        .eq(StudyLeader::getActivityId, activityId)
        );

        List<Long> userIds = prevMembers.stream().map(StudyMember::getUserId)
                .distinct().collect(Collectors.toList());
        Collections.shuffle(userIds);

        int leaderCount = leaders.size();
        if (leaderCount == 0) return;

        for (int i = 0; i < userIds.size(); i++) {
            StudyMember newMember = new StudyMember();
            newMember.setActivityId(activityId);
            newMember.setUserId(userIds.get(i));
            newMember.setWeek(newWeek);
            newMember.setLeaderId(leaders.get(i % leaderCount).getId());
            studyMemberMapper.insert(newMember);
        }
    }

    // ============= 作业管理 =============

    /**
     * 上传/替换作业文件（会长操作，一个周期一个文件）
     */
    @Transactional
    public void uploadHomework(Long activityId, Integer week, String title, String fileUrl, String fileName) {
        // 先删除已有的作业
        studyMaterialMapper.delete(
                new LambdaQueryWrapper<StudyMaterial>()
                        .eq(StudyMaterial::getActivityId, activityId)
                        .eq(StudyMaterial::getWeek, week)
                        .eq(StudyMaterial::getFileType, 1)
        );

        StudyMaterial material = new StudyMaterial();
        material.setActivityId(activityId);
        material.setWeek(week);
        material.setFileType(1);
        material.setTitle(title != null ? title : fileName);
        material.setFileUrl(fileUrl);
        material.setFileName(fileName);
        material.setUserId(Long.parseLong(cn.dev33.satoken.stp.StpUtil.getLoginId().toString()));
        studyMaterialMapper.insert(material);
    }

    /**
     * 删除指定周期的作业文件
     */
    public void deleteHomework(Long activityId, Integer week) {
        studyMaterialMapper.delete(
                new LambdaQueryWrapper<StudyMaterial>()
                        .eq(StudyMaterial::getActivityId, activityId)
                        .eq(StudyMaterial::getWeek, week)
                        .eq(StudyMaterial::getFileType, 1)
        );
    }

    /**
     * 学生删除自己提交的作业
     */
    public void deleteMySubmission(Long activityId, Integer week, Long userId) {
        studyMaterialMapper.delete(
                new LambdaQueryWrapper<StudyMaterial>()
                        .eq(StudyMaterial::getActivityId, activityId)
                        .eq(StudyMaterial::getWeek, week)
                        .eq(StudyMaterial::getFileType, 2)
                        .eq(StudyMaterial::getUserId, userId)
        );
    }

    /**
     * 学生提交作业（fileType=2），先删除旧提交再新增
     */
    @Transactional
    public void submitStudentHomework(Long activityId, Integer week, Long userId, String title, String fileUrl, String fileName) {
        // 先删除旧提交
        studyMaterialMapper.delete(
                new LambdaQueryWrapper<StudyMaterial>()
                        .eq(StudyMaterial::getActivityId, activityId)
                        .eq(StudyMaterial::getWeek, week)
                        .eq(StudyMaterial::getFileType, 2)
                        .eq(StudyMaterial::getUserId, userId)
        );

        StudyMaterial material = new StudyMaterial();
        material.setActivityId(activityId);
        material.setWeek(week);
        material.setFileType(2);
        material.setTitle(title != null ? title : fileName);
        material.setFileUrl(fileUrl);
        material.setFileName(fileName);
        material.setUserId(userId);
        studyMaterialMapper.insert(material);
    }

    /**
     * 设置活动周期
     */
    public void setActiveWeek(Long activityId, Integer week) {
        StudyActivity activity = studyActivityMapper.selectById(activityId);
        if (activity == null) throw new BusinessException("活动不存在");
        if (week < 1 || week > activity.getCurrentWeek()) {
            throw new BusinessException("周期范围无效，当前最大周期为 " + activity.getCurrentWeek());
        }
        activity.setActiveWeek(week);
        studyActivityMapper.updateById(activity);
    }

    // ============= 评分 =============

    public void score(StudyScore studyScore) {
        if (studyScore.getScore() < 1 || studyScore.getScore() > 10) {
            throw new BusinessException("分数范围为1-10");
        }
        if (studyScore.getComment() == null || studyScore.getComment().length() < 10) {
            throw new BusinessException("评语不得少于10个字");
        }

        // 权限校验：评分人必须是该活动的负责人，且只能给分配给自己的成员评分
        StudyLeader leader = studyLeaderMapper.selectOne(
                new LambdaQueryWrapper<StudyLeader>()
                        .eq(StudyLeader::getActivityId, studyScore.getActivityId())
                        .eq(StudyLeader::getUserId, studyScore.getLeaderUserId())
                        .last("LIMIT 1"));
        if (leader == null) {
            throw new BusinessException("无权评分：您不是该学习活动的负责人");
        }
        StudyMember member = studyMemberMapper.selectOne(
                new LambdaQueryWrapper<StudyMember>()
                        .eq(StudyMember::getActivityId, studyScore.getActivityId())
                        .eq(StudyMember::getUserId, studyScore.getMemberUserId())
                        .eq(StudyMember::getWeek, studyScore.getWeek())
                        .last("LIMIT 1"));
        if (member == null) {
            throw new BusinessException("该成员本周期不在学习名单中");
        }
        if (member.getLeaderId() != null && !member.getLeaderId().equals(leader.getId())) {
            throw new BusinessException("无权评分：该成员未分配给您");
        }

        LambdaQueryWrapper<StudyScore> check = new LambdaQueryWrapper<>();
        check.eq(StudyScore::getActivityId, studyScore.getActivityId())
                .eq(StudyScore::getWeek, studyScore.getWeek())
                .eq(StudyScore::getMemberUserId, studyScore.getMemberUserId());
        if (studyScoreMapper.selectCount(check) > 0) {
            throw new BusinessException("该成员本周期已评分");
        }
        try {
            studyScoreMapper.insert(studyScore);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发评分：唯一约束 uk_activity_week_member 兜底
            throw new BusinessException("该成员本周期已评分");
        }
    }

    public List<Map<String, Object>> scoreOverview(Long activityId, Integer week) {
        LambdaQueryWrapper<StudyScore> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudyScore::getActivityId, activityId);
        if (week != null) wrapper.eq(StudyScore::getWeek, week);
        wrapper.orderByDesc(StudyScore::getScore);
        List<StudyScore> scores = studyScoreMapper.selectList(wrapper);

        return scores.stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getId());
            map.put("week", s.getWeek());
            map.put("score", s.getScore());
            map.put("comment", s.getComment());
            map.put("createdAt", s.getCreatedAt());
            map.put("memberName", cacheService.getUserName(s.getMemberUserId()));
            map.put("memberStudentId", cacheService.getStudentId(s.getMemberUserId()));
            map.put("leaderName", cacheService.getUserName(s.getLeaderUserId()));
            return map;
        }).collect(Collectors.toList());
    }

    // ============= 统计 =============

    public long countStudyActivities() {
        return studyActivityMapper.selectCount(null);
    }

    public long countStudyActivitiesByGrade(String grade) {
        return studyActivityMapper.selectCount(
                new LambdaQueryWrapper<StudyActivity>().eq(StudyActivity::getGrade, grade)
        );
    }

    /**
     * 关闭活动
     */
    public void closeActivity(Long activityId) {
        StudyActivity activity = studyActivityMapper.selectById(activityId);
        if (activity == null) throw new BusinessException("活动不存在");
        activity.setStatus(0);
        studyActivityMapper.updateById(activity);
    }

    // ============= 用户端专用 =============

    /**
     * 查询用户在当前年级最新活动中的学习状态
     */
    public Map<String, Object> getMyStatus(Long userId) {
        Setting gradeSetting = settingMapper.selectOne(
                new LambdaQueryWrapper<Setting>().eq(Setting::getSettingKey, "current_grade")
        );
        String currentGrade = gradeSetting != null ? gradeSetting.getSettingValue() : "2025";

        // 查找当前年级最新的进行中活动
        StudyActivity latest = studyActivityMapper.selectOne(
                new LambdaQueryWrapper<StudyActivity>()
                        .eq(StudyActivity::getGrade, currentGrade)
                        .eq(StudyActivity::getStatus, 1)
                        .orderByDesc(StudyActivity::getSeqNum)
                        .last("LIMIT 1")
        );

        Map<String, Object> result = new HashMap<>();
        if (latest == null) {
            result.put("hasActivity", false);
            return result;
        }

        result.put("hasActivity", true);
        result.put("activity", latest);

        // 检查用户是否已加入该活动（不限周期，加入的是活动）
        Long memberCount = studyMemberMapper.selectCount(
                new LambdaQueryWrapper<StudyMember>()
                        .eq(StudyMember::getActivityId, latest.getId())
                        .eq(StudyMember::getUserId, userId)
        );
        result.put("joined", memberCount > 0);

        // 当前周期作业
        StudyMaterial homework = studyMaterialMapper.selectOne(
                new LambdaQueryWrapper<StudyMaterial>()
                        .eq(StudyMaterial::getActivityId, latest.getId())
                        .eq(StudyMaterial::getWeek, latest.getActiveWeek())
                        .eq(StudyMaterial::getFileType, 1)
        );
        result.put("homework", homework);

        // 用户本周期提交情况
        if (memberCount > 0) {
            List<StudyMaterial> submissions = studyMaterialMapper.selectList(
                    new LambdaQueryWrapper<StudyMaterial>()
                            .eq(StudyMaterial::getActivityId, latest.getId())
                            .eq(StudyMaterial::getWeek, latest.getActiveWeek())
                            .eq(StudyMaterial::getFileType, 2)
                            .eq(StudyMaterial::getUserId, userId)
            );
            result.put("submitted", !submissions.isEmpty());
            result.put("submissions", submissions);

            // 当前周期的评分
            StudyScore weekScore = studyScoreMapper.selectOne(
                    new LambdaQueryWrapper<StudyScore>()
                            .eq(StudyScore::getActivityId, latest.getId())
                            .eq(StudyScore::getWeek, latest.getActiveWeek())
                            .eq(StudyScore::getMemberUserId, userId)
            );
            if (weekScore != null) {
                Map<String, Object> scoreInfo = new HashMap<>();
                scoreInfo.put("score", weekScore.getScore());
                scoreInfo.put("comment", weekScore.getComment());
                scoreInfo.put("leaderName", cacheService.getUserName(weekScore.getLeaderUserId()));
                scoreInfo.put("createdAt", weekScore.getCreatedAt());
                result.put("weekScore", scoreInfo);
            }
        }

        return result;
    }

    /**
     * 查询用户在所有活动中的成绩
     */
    public Map<String, Object> getMyScores(Long userId) {
        // 获取当前年级
        Setting gradeSetting = settingMapper.selectOne(
                new LambdaQueryWrapper<Setting>().eq(Setting::getSettingKey, "current_grade")
        );
        String currentGrade = gradeSetting != null ? gradeSetting.getSettingValue() : "2025";

        // 只查询当前年级中进行中的活动
        List<StudyActivity> gradeActivities = studyActivityMapper.selectList(
                new LambdaQueryWrapper<StudyActivity>()
                        .eq(StudyActivity::getGrade, currentGrade)
                        .eq(StudyActivity::getStatus, 1)
                        .orderByDesc(StudyActivity::getSeqNum)
        );

        Map<String, Object> result = new HashMap<>();
        if (gradeActivities.isEmpty()) {
            result.put("activities", List.of());
            return result;
        }

        Set<Long> gradeActivityIds = gradeActivities.stream()
                .map(StudyActivity::getId).collect(Collectors.toSet());

        // 获取用户在当前年级活动中的成员记录
        List<StudyMember> memberships = studyMemberMapper.selectList(
                new LambdaQueryWrapper<StudyMember>()
                        .eq(StudyMember::getUserId, userId)
                        .in(StudyMember::getActivityId, gradeActivityIds)
        );

        Set<Long> joinedActivityIds = memberships.stream()
                .map(StudyMember::getActivityId).collect(Collectors.toSet());

        if (joinedActivityIds.isEmpty()) {
            result.put("activities", List.of());
            return result;
        }

        // 只保留用户参与过的当前年级活动
        List<StudyActivity> activities = gradeActivities.stream()
                .filter(a -> joinedActivityIds.contains(a.getId()))
                .collect(Collectors.toList());

        // 获取用户在这些活动中的所有成绩
        List<StudyScore> allScores = studyScoreMapper.selectList(
                new LambdaQueryWrapper<StudyScore>()
                        .eq(StudyScore::getMemberUserId, userId)
                        .in(StudyScore::getActivityId, joinedActivityIds)
        );
        Map<Long, List<StudyScore>> scoresByActivity = allScores.stream()
                .collect(Collectors.groupingBy(StudyScore::getActivityId));

        List<Map<String, Object>> activityList = activities.stream().map(a -> {
            Map<String, Object> am = new HashMap<>();
            am.put("activityId", a.getId());
            am.put("title", a.getTitle());
            am.put("grade", a.getGrade());
            am.put("seqNum", a.getSeqNum());
            am.put("status", a.getStatus());

            List<StudyScore> scores = scoresByActivity.getOrDefault(a.getId(), List.of());
            List<Map<String, Object>> scoreList = scores.stream().map(s -> {
                Map<String, Object> sm = new HashMap<>();
                sm.put("week", s.getWeek());
                sm.put("score", s.getScore());
                sm.put("comment", s.getComment());
                sm.put("createdAt", s.getCreatedAt());
                sm.put("leaderName", cacheService.getUserName(s.getLeaderUserId()));
                return sm;
            }).collect(Collectors.toList());
            am.put("scores", scoreList);

            int totalScore = scores.stream().mapToInt(StudyScore::getScore).sum();
            am.put("totalScore", totalScore);

            // 计算排名：查该活动所有人的总分
            List<StudyScore> allActivityScores = studyScoreMapper.selectList(
                    new LambdaQueryWrapper<StudyScore>()
                            .eq(StudyScore::getActivityId, a.getId())
            );
            Map<Long, Integer> userTotals = new HashMap<>();
            for (StudyScore s : allActivityScores) {
                userTotals.merge(s.getMemberUserId(), s.getScore(), Integer::sum);
            }
            long rank = userTotals.values().stream().filter(t -> t > totalScore).count() + 1;
            am.put("rank", rank);
            am.put("totalParticipants", userTotals.size());

            return am;
        }).collect(Collectors.toList());

        result.put("activities", activityList);
        return result;
    }

    /**
     * 成绩排名（分页，按总分倒序）
     */
    public Map<String, Object> getRanking(Long activityId, int current, int size) {
        List<StudyScore> allScores = studyScoreMapper.selectList(
                new LambdaQueryWrapper<StudyScore>()
                        .eq(StudyScore::getActivityId, activityId)
        );

        // 按用户汇总总分
        Map<Long, Integer> userTotals = new HashMap<>();
        for (StudyScore s : allScores) {
            userTotals.merge(s.getMemberUserId(), s.getScore(), Integer::sum);
        }

        // 按总分倒序排序
        List<Map.Entry<Long, Integer>> sorted = userTotals.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .collect(Collectors.toList());

        int total = sorted.size();
        int from = (current - 1) * size;
        int to = Math.min(from + size, total);

        List<Map<String, Object>> records = new ArrayList<>();
        for (int i = from; i < to; i++) {
            Map.Entry<Long, Integer> entry = sorted.get(i);
            Map<String, Object> map = new HashMap<>();
            map.put("rank", i + 1);
            map.put("userId", entry.getKey());
            map.put("totalScore", entry.getValue());
            map.put("userName", cacheService.getUserName(entry.getKey()));
            map.put("studentId", cacheService.getStudentId(entry.getKey()));
            records.add(map);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        return result;
    }
}
