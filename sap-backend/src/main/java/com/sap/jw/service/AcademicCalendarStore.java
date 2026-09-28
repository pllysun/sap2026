package com.sap.jw.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sap.common.BusinessException;
import com.sap.entity.Setting;
import com.sap.mapper.SettingMapper;
import com.sap.service.SettingService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 全校共用校历。沿用独立的 term_start_ 存储，保留已采集数据，不依赖任何个人课表。 */
@Service
public class AcademicCalendarStore {
    private static final String PREFIX = "term_start_";
    private final SettingMapper mapper;
    private final SettingService settings;

    public AcademicCalendarStore(SettingMapper mapper, SettingService settings) {
        this.mapper = mapper;
        this.settings = settings;
    }

    public record Entry(String term, String semesterStartDate, String source, LocalDateTime updatedAt) {}

    public List<Entry> list() {
        return mapper.selectList(new LambdaQueryWrapper<Setting>()
                        .likeRight(Setting::getSettingKey, PREFIX).orderByDesc(Setting::getSettingKey))
                .stream().filter(s -> s.getSettingKey().startsWith(PREFIX))
                .filter(s -> valid(s.getSettingKey().substring(PREFIX.length()), s.getSettingValue()))
                .map(s -> new Entry(s.getSettingKey().substring(PREFIX.length()), s.getSettingValue(),
                        s.getDescription() != null && s.getDescription().startsWith("手动维护") ? "MANUAL" : "SCHOOL", s.getUpdatedAt()))
                .toList();
    }

    public String find(String term) {
        if (!validTerm(term)) return null;
        String date = settings.getValue(PREFIX + term);
        return valid(term, date) ? date : null;
    }

    public synchronized void save(String term, String date, boolean manual, Long operator) {
        if (!valid(term, date)) throw new BusinessException(400, "请输入有效的学期和对应年份的开学日期");
        // 采集只补缺，不覆盖管理员刚刚维护的校历。
        if (!manual && find(term) != null) return;
        Setting setting = new Setting();
        setting.setSettingKey(PREFIX + term);
        setting.setSettingValue(date);
        setting.setDescription(manual ? "手动维护校历，操作人 ID=" + operator : "学校教学日历(第1周周一)");
        settings.updateSetting(setting);
    }

    public synchronized void delete(String term) {
        if (!validTerm(term)) throw new BusinessException(400, "学期格式应为 2026-2027-1");
        mapper.delete(new LambdaQueryWrapper<Setting>().eq(Setting::getSettingKey, PREFIX + term));
    }

    static boolean validTerm(String term) {
        if (term == null || !term.matches("\\d{4}-\\d{4}-[12]")) return false;
        return Integer.parseInt(term.substring(5, 9)) == Integer.parseInt(term.substring(0, 4)) + 1;
    }

    static boolean valid(String term, String date) {
        if (!validTerm(term) || date == null) return false;
        try {
            LocalDate parsed = LocalDate.parse(date);
            int year = Integer.parseInt(term.substring(term.endsWith("1") ? 0 : 5, term.endsWith("1") ? 4 : 9));
            return parsed.getYear() == year;
        } catch (RuntimeException e) { return false; }
    }
}
