package com.sap.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.entity.Setting;
import com.sap.entity.Term;
import com.sap.mapper.SettingMapper;
import com.sap.mapper.TermMapper;
import com.sap.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired private ActivityService activityService;
    @Autowired private BillService billService;
    @Autowired private StudyService studyService;
    @Autowired private SettingMapper settingMapper;
    @Autowired private TermMapper termMapper;

    @GetMapping("/stats")
    @OperationLog("查询仪表盘统计")
    public Result<?> stats(@RequestParam(required = false) String grade) {
        // 如果没传grade，使用当前年级
        String currentGrade = grade;
        if (currentGrade == null || currentGrade.isEmpty()) {
            Setting gradeSetting = settingMapper.selectOne(
                    new LambdaQueryWrapper<Setting>().eq(Setting::getSettingKey, "current_grade")
            );
            currentGrade = gradeSetting != null ? gradeSetting.getSettingValue() : "2025";
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("currentGrade", currentGrade);

        if ("all".equals(grade)) {
            // 全部数据
            Long memberCount = termMapper.selectCount(null);
            stats.put("userCount", memberCount);
            stats.put("activityCount", activityService.countActivities());
            stats.put("studyActivityCount", studyService.countStudyActivities());
            stats.put("financeStats", billService.getStats(null));
        } else {
            Long memberCount = termMapper.selectCount(
                    new LambdaQueryWrapper<Term>().eq(Term::getGrade, currentGrade)
            );
            stats.put("userCount", memberCount);
            stats.put("activityCount", activityService.countActivitiesByGrade(currentGrade));
            stats.put("studyActivityCount", studyService.countStudyActivitiesByGrade(currentGrade));
            stats.put("financeStats", billService.getStats(currentGrade));
        }
        return Result.ok(stats);
    }

    @GetMapping("/grade-stats")
    @OperationLog("查询历届成员统计")
    public Result<?> gradeStats() {
        LambdaQueryWrapper<Term> gradeWrapper = new LambdaQueryWrapper<>();
        gradeWrapper.select(Term::getGrade).groupBy(Term::getGrade).orderByAsc(Term::getGrade);
        List<Term> gradeTerms = termMapper.selectList(gradeWrapper);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Term gt : gradeTerms) {
            String g = gt.getGrade();
            Long count = termMapper.selectCount(
                    new LambdaQueryWrapper<Term>().eq(Term::getGrade, g)
            );
            Map<String, Object> item = new HashMap<>();
            item.put("grade", g);
            item.put("count", count);
            result.add(item);
        }
        return Result.ok(result);
    }
}
