package com.sap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sap.common.BusinessException;
import com.sap.entity.Setting;
import com.sap.entity.Term;
import com.sap.entity.User;
import com.sap.mapper.SettingMapper;
import com.sap.mapper.TermMapper;
import com.sap.mapper.UserMapper;
import com.sap.mapper.PositionMapper;
import com.sap.mapper.StudyActivityMapper;
import com.sap.entity.Position;
import com.sap.entity.StudyActivity;
import com.sap.service.CacheService;
import com.sap.mapper.UserRoleMapper;
import com.sap.entity.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TermService {

    @Autowired
    private TermMapper termMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PositionMapper positionMapper;
    @Autowired
    private SettingMapper settingMapper;
    @Autowired
    private UserRoleMapper userRoleMapper;
    @Autowired
    private CacheService cacheService;
    @Autowired
    private StudyActivityMapper studyActivityMapper;
    @Autowired
    private JoinService joinService;

    /**
     * 按年级分页查询换届信息（按权限排序：会长 > 副会长 > 部长 > 成员）
     */
    public Page<Map<String, Object>> listByGradePaged(String grade, int current, int size) {
        // 1. 查出该年级所有换届记录
        LambdaQueryWrapper<Term> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Term::getGrade, grade);
        List<Term> allTerms = termMapper.selectList(wrapper);
        if (allTerms.isEmpty()) return new Page<>(current, size, 0);

        // 2. 构建 positionId -> sortOrder 映射（用于显示排序）
        Map<Integer, Integer> posSortMap = new HashMap<>();
        for (Term t : allTerms) {
            if (!posSortMap.containsKey(t.getPositionId())) {
                Position pos = positionMapper.selectById(t.getPositionId());
                posSortMap.put(t.getPositionId(), pos != null && pos.getSortOrder() != null ? pos.getSortOrder() : 999);
            }
        }

        // 3. 按 sortOrder 升序排列（排名越靠前数值越小），同身份按 id 升序
        allTerms.sort(Comparator.<Term, Integer>comparing(t -> posSortMap.getOrDefault(t.getPositionId(), 999))
                .thenComparing(Term::getId));

        // 4. 手动分页
        int total = allTerms.size();
        int from = (current - 1) * size;
        int to = Math.min(from + size, total);
        List<Term> pageTerms = from < total ? allTerms.subList(from, to) : List.of();

        // 5. 转换结果
        Page<Map<String, Object>> resultPage = new Page<>(current, size, total);
        List<Map<String, Object>> records = new ArrayList<>();
        for (Term term : pageTerms) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", term.getId());
            item.put("grade", term.getGrade());
            item.put("userName", cacheService.getUserName(term.getUserId()));
            item.put("studentId", cacheService.getStudentId(term.getUserId()));
            item.put("userId", term.getUserId());
            item.put("positionName", cacheService.getPositionName(term.getPositionId()));
            item.put("positionId", term.getPositionId());
            records.add(item);
        }
        resultPage.setRecords(records);
        return resultPage;
    }

    /**
     * 按年级查询换届信息
     */
    public List<Map<String, Object>> listByGrade(String grade) {
        LambdaQueryWrapper<Term> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Term::getGrade, grade).orderByAsc(Term::getId);
        List<Term> terms = termMapper.selectList(wrapper);
        if (terms.isEmpty()) return List.of();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Term term : terms) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", term.getId());
            item.put("grade", term.getGrade());
            item.put("userName", cacheService.getUserName(term.getUserId()));
            item.put("studentId", cacheService.getStudentId(term.getUserId()));
            item.put("userId", term.getUserId());
            item.put("positionName", cacheService.getPositionName(term.getPositionId()));
            item.put("positionId", term.getPositionId());
            result.add(item);
        }
        return result;
    }

    /**
     * 获取所有年级列表
     */
    public List<String> listGrades() {
        LambdaQueryWrapper<Term> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Term::getGrade).groupBy(Term::getGrade).orderByDesc(Term::getGrade);
        List<Term> terms = termMapper.selectList(wrapper);
        return terms.stream().map(Term::getGrade).collect(Collectors.toList());
    }

    /**
     * 新增换届记录
     */
    public void addTerm(Term term) {
        // 从设置中读取当前年级
        if (term.getGrade() == null || term.getGrade().isEmpty()) {
            Setting gradeSetting = settingMapper.selectOne(
                    new LambdaQueryWrapper<Setting>().eq(Setting::getSettingKey, "current_grade")
            );
            term.setGrade(gradeSetting != null ? gradeSetting.getSettingValue() : "2025");
        }
        // 检查是否存在重复
        LambdaQueryWrapper<Term> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Term::getUserId, term.getUserId())
                .eq(Term::getGrade, term.getGrade())
                .eq(Term::getPositionId, term.getPositionId());
        if (termMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("该用户在该年级已存在相同身份");
        }
        termMapper.insert(term);
    }

    /**
     * 删除换届记录
     */
    public void deleteTerm(Long id) {
        termMapper.deleteById(id);
    }

    /**
     * 执行换届
     * @param assignments 选人列表 [{positionId, userIds:[]}]
     */
    @Transactional
    public void changeover(List<Map<String, Object>> assignments) {
        // 1. 获取当前年级
        Setting setting = settingMapper.selectOne(
                new LambdaQueryWrapper<Setting>().eq(Setting::getSettingKey, "current_grade")
        );
        if (setting == null) throw new BusinessException("未找到年级设置");
        int currentGradeNum;
        try {
            currentGradeNum = Integer.parseInt(setting.getSettingValue());
        } catch (NumberFormatException e) {
            throw new BusinessException("当前年级配置非法（应为数字年份）：" + setting.getSettingValue());
        }
        String newGrade = String.valueOf(currentGradeNum + 1);

        // 2. 获取所有非"成员"的身份（需要换届选人的身份）
        List<Position> allPositions = positionMapper.selectList(
                new LambdaQueryWrapper<Position>().ne(Position::getPositionName, "成员")
        );
        Map<Integer, Position> positionMap = allPositions.stream()
                .collect(Collectors.toMap(Position::getId, p -> p));

        // 3. 校验会长和团支书必选
        Set<Integer> assignedPositionIds = new HashSet<>();
        if (assignments != null) {
            for (Map<String, Object> a : assignments) {
                Integer posId = Integer.valueOf(a.get("positionId").toString());
                assignedPositionIds.add(posId);
            }
        }
        for (Position pos : allPositions) {
            if (pos.getIsSystem() != null && pos.getIsSystem() == 1) {
                if (!assignedPositionIds.contains(pos.getId())) {
                    throw new BusinessException("必须选择 " + pos.getPositionName());
                }
            }
        }

        // 3.5 校验一人一职：同一个人不能出现在多个职位中
        Set<Long> allAssignedUsers = new HashSet<>();
        if (assignments != null) {
            for (Map<String, Object> a : assignments) {
                List<?> rawUserIds = (List<?>) a.get("userIds");
                if (rawUserIds == null) continue;
                for (Object o : rawUserIds) {
                    Long uid = Long.valueOf(o.toString());
                    if (allAssignedUsers.contains(uid)) {
                        String userName = cacheService.getUserName(uid);
                        throw new BusinessException(userName + " 已被分配到其他职位，一人只能担任一个职位");
                    }
                    allAssignedUsers.add(uid);
                }
            }
        }

        // 4. 校验人数限制 + 写入换届记录 + 赋权限
        if (assignments != null) {
            for (Map<String, Object> a : assignments) {
                Integer posId = Integer.valueOf(a.get("positionId").toString());
                @SuppressWarnings("unchecked")
                List<?> rawUserIds = (List<?>) a.get("userIds");
                if (rawUserIds == null || rawUserIds.isEmpty()) continue;

                List<Long> userIds = rawUserIds.stream()
                        .map(o -> Long.valueOf(o.toString()))
                        .collect(Collectors.toList());

                Position pos = positionMap.get(posId);
                if (pos == null) {
                    throw new BusinessException("身份ID不存在: " + posId);
                }

                // 校验人数
                if (userIds.size() > pos.getMaxCount()) {
                    throw new BusinessException(pos.getPositionName() + " 最多选 " +
                            pos.getMaxCount() + " 人，当前选了 " + userIds.size() +
                            " 人，请先修改身份数量上限");
                }

                // 为每个选中用户写入新年级换届记录 + 赋权限
                for (Long userId : userIds) {
                    // 写入换届记录
                    Term term = new Term();
                    term.setUserId(userId);
                    term.setGrade(newGrade);
                    term.setPositionId(posId);
                    termMapper.insert(term);

                    // 赋予权限（如果该用户还没有该权限码）
                    Integer roleCode = pos.getRoleCode();
                    if (roleCode != null) {
                        Long existCount = userRoleMapper.selectCount(
                                new LambdaQueryWrapper<UserRole>()
                                        .eq(UserRole::getUserId, userId)
                                        .eq(UserRole::getRoleCode, roleCode)
                        );
                        if (existCount == 0) {
                            UserRole userRole = new UserRole();
                            userRole.setUserId(userId);
                            userRole.setRoleCode(roleCode);
                            userRoleMapper.insert(userRole);
                        }
                    }
                }
            }
        }

        // 5. 年级 +1
        setting.setSettingValue(newGrade);
        settingMapper.updateById(setting);

        // 6. 归档所有进行中的学习活动（同一事务内）
        StudyActivity archiveUpdate = new StudyActivity();
        archiveUpdate.setStatus(0);
        studyActivityMapper.update(archiveUpdate,
                new LambdaQueryWrapper<StudyActivity>().eq(StudyActivity::getStatus, 1));

        // 7. 自动初始化新年级的入会负责人
        joinService.initManagers();
    }
}
