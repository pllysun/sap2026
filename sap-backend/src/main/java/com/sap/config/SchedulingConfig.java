package com.sap.config;

import com.sap.service.StudyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 定时任务配置。
 * <p>核心：学习小组作业「定时发布」——每分钟检查是否有作业到达发布时间，
 * 到点则自动推进对应活动的周期并重新匹配负责人。</p>
 */
@Slf4j
@Configuration
@EnableScheduling
public class SchedulingConfig {

    private final StudyService studyService;

    public SchedulingConfig(StudyService studyService) {
        this.studyService = studyService;
    }

    /** 每分钟检查一次作业发布时间，到点自动推进学习活动周期。 */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void publishScheduledHomework() {
        try {
            int advanced = studyService.processScheduledPublish();
            if (advanced > 0) {
                log.info("[StudySchedule] 本轮自动推进 {} 个周期", advanced);
            }
        } catch (Exception e) {
            log.error("[StudySchedule] 定时发布处理失败", e);
        }
    }
}
