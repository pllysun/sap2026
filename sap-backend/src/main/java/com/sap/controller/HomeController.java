package com.sap.controller;

import com.sap.common.Result;
import com.sap.mapper.HomeOverviewMapper;
import com.sap.mapper.NoteMapper;
import com.sap.service.ActivityService;
import com.sap.service.StudyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/** 所有登录用户均可查看；不返回财务、账号或其他管理数据。 */
@RestController
@RequestMapping("/api/home")
public class HomeController {
    private final HomeOverviewMapper overview;
    private final ActivityService activities;
    private final StudyService study;
    private final NoteMapper notes;

    public HomeController(HomeOverviewMapper overview, ActivityService activities,
                          StudyService study, NoteMapper notes) {
        this.overview = overview;
        this.activities = activities;
        this.study = study;
        this.notes = notes;
    }

    @GetMapping("/overview")
    public Result<?> overview() {
        return Result.ok(Map.of(
            "registeredCount", overview.registeredCount(),
            "memberCount", overview.memberCount(),
            "activityCount", activities.countActivities(),
            "studyActivityCount", study.countStudyActivities(),
            "noteCount", notes.selectCount(null),
            "membersByGrade", overview.membersByGrade(),
            "memberArchiveByTerm", overview.memberArchiveByTerm()));
    }
}
