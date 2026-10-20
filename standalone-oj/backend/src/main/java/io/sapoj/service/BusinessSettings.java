package io.sapoj.service;
import io.sapoj.common.BusinessException;
import io.sapoj.config.judger.JudgerProperties;
import io.sapoj.entity.SiteSettings;
import io.sapoj.mapper.SiteSettingsMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor @DependsOn("entityManagerFactory")
public class BusinessSettings {
    private final SiteSettingsMapper mapper;
    private final JudgerProperties judge;
    private volatile SiteSettings active;
    @PostConstruct public void initialize(){
        var row=mapper.selectById(1L);
        if(row==null){row=new SiteSettings();mapper.insert(row);}
        apply(row);
    }
    public SiteSettings get(){var row=new SiteSettings();row.setSiteName(active.getSiteName());row.setRegistrationEnabled(active.getRegistrationEnabled());row.setQueueCapacity(active.getQueueCapacity());row.setDailySubmissionLimit(active.getDailySubmissionLimit());row.setMaxJudgeGroupMs(active.getMaxJudgeGroupMs());return row;}
    public synchronized SiteSettings save(SiteSettings row){
        if(row.getSiteName()==null||row.getSiteName().isBlank()||row.getSiteName().length()>60||row.getRegistrationEnabled()==null
            ||row.getQueueCapacity()==null||row.getQueueCapacity()<1||row.getQueueCapacity()>1000
            ||row.getDailySubmissionLimit()==null||row.getDailySubmissionLimit()<0||row.getDailySubmissionLimit()>100000
            ||row.getMaxJudgeGroupMs()==null||row.getMaxJudgeGroupMs()<10000||row.getMaxJudgeGroupMs()>1800000)
            throw new BusinessException(400,"设置无效：队列 1–1000，每日限制 0–100000，判题组时限 10000–1800000 ms");
        row.setId(1L);row.setSiteName(row.getSiteName().strip());mapper.updateById(row);apply(row);return row;
    }
    private void apply(SiteSettings row){
        judge.setQueueCapacity(row.getQueueCapacity());judge.setDailySubmissionLimit(row.getDailySubmissionLimit());judge.setMaxJudgeGroupMs(row.getMaxJudgeGroupMs());active=row;
    }
}
