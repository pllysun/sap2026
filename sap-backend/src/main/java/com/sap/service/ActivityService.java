package com.sap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sap.common.BusinessException;
import com.sap.entity.Activity;
import com.sap.entity.ActivityImage;
import com.sap.mapper.ActivityImageMapper;
import com.sap.mapper.ActivityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import com.sap.entity.Setting;
import com.sap.mapper.SettingMapper;

@Service
public class ActivityService {

    @Autowired
    private ActivityMapper activityMapper;
    @Autowired
    private ActivityImageMapper activityImageMapper;
    @Autowired
    private SettingMapper settingMapper;

    /**
     * 按年级查询活动列表
     */
    public List<Map<String, Object>> listByGrade(String grade) {
        LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Activity::getGrade, grade).orderByDesc(Activity::getSeqNum);
        List<Activity> activities = activityMapper.selectList(wrapper);

        return activities.stream().map(a -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", a.getId());
            map.put("grade", a.getGrade());
            map.put("seqNum", a.getSeqNum());
            map.put("title", a.getTitle());
            map.put("content", a.getContent());
            map.put("createdAt", a.getCreatedAt());
            // 获取图片
            List<ActivityImage> images = activityImageMapper.selectList(
                    new LambdaQueryWrapper<ActivityImage>()
                            .eq(ActivityImage::getActivityId, a.getId())
                            .orderByAsc(ActivityImage::getSortOrder)
            );
            map.put("images", images);
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 分页查询所有活动（按年份、ID降序）
     */
    public Map<String, Object> pageAll(int current, int size) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Activity> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size);
        LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Activity::getGrade).orderByDesc(Activity::getId);
        activityMapper.selectPage(page, wrapper);

        List<Map<String, Object>> records = page.getRecords().stream().map(a -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", a.getId());
            map.put("grade", a.getGrade());
            map.put("seqNum", a.getSeqNum());
            map.put("title", a.getTitle());
            map.put("content", a.getContent());
            map.put("createdAt", a.getCreatedAt());
            List<ActivityImage> images = activityImageMapper.selectList(
                    new LambdaQueryWrapper<ActivityImage>()
                            .eq(ActivityImage::getActivityId, a.getId())
                            .orderByAsc(ActivityImage::getSortOrder)
            );
            map.put("images", images);
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", page.getTotal());
        result.put("current", page.getCurrent());
        result.put("pages", page.getPages());
        return result;
    }

    /**
     * 新增活动
     */
    @Transactional
    public void addActivity(Activity activity, List<String> imageUrls) {
        // 使用前端传来的grade，如果没传则用current_grade
        if (activity.getGrade() == null || activity.getGrade().isEmpty()) {
            Setting gradeSetting = settingMapper.selectOne(
                    new LambdaQueryWrapper<Setting>().eq(Setting::getSettingKey, "current_grade")
            );
            activity.setGrade(gradeSetting != null ? gradeSetting.getSettingValue() : "2025");
        }

        // 取该年级当前最大序号 + 1，而非 count + 1：
        // 软删除会让 count 回退，删除中间活动后再新增会与已有序号重复
        Activity last = activityMapper.selectOne(
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getGrade, activity.getGrade())
                        .orderByDesc(Activity::getSeqNum)
                        .last("LIMIT 1")
        );
        int nextSeq = (last != null && last.getSeqNum() != null) ? last.getSeqNum() + 1 : 1;
        activity.setSeqNum(nextSeq);

        activityMapper.insert(activity);

        // 保存图片
        if (imageUrls != null) {
            for (int i = 0; i < imageUrls.size(); i++) {
                ActivityImage img = new ActivityImage();
                img.setActivityId(activity.getId());
                img.setImageUrl(imageUrls.get(i));
                img.setSortOrder(i);
                activityImageMapper.insert(img);
            }
        }
    }

    /**
     * 更新活动
     */
    @Transactional
    public void updateActivity(Long id, Activity activity, List<String> imageUrls) {
        Activity existing = activityMapper.selectById(id);
        if (existing == null) throw new BusinessException("活动不存在");
        existing.setTitle(activity.getTitle());
        existing.setContent(activity.getContent());
        activityMapper.updateById(existing);

        // 更新图片：删除旧图片，添加新图片
        if (imageUrls != null) {
            activityImageMapper.delete(
                    new LambdaQueryWrapper<ActivityImage>().eq(ActivityImage::getActivityId, id)
            );
            for (int i = 0; i < imageUrls.size(); i++) {
                ActivityImage img = new ActivityImage();
                img.setActivityId(id);
                img.setImageUrl(imageUrls.get(i));
                img.setSortOrder(i);
                activityImageMapper.insert(img);
            }
        }
    }

    /**
     * 删除活动（级联删除关联图片，避免产生孤儿数据）
     */
    @Transactional
    public void deleteActivity(Long id) {
        activityMapper.deleteById(id);
        activityImageMapper.delete(
                new LambdaQueryWrapper<ActivityImage>().eq(ActivityImage::getActivityId, id)
        );
    }

    public long countActivities() {
        return activityMapper.selectCount(null);
    }

    public long countActivitiesByGrade(String grade) {
        return activityMapper.selectCount(
                new LambdaQueryWrapper<Activity>().eq(Activity::getGrade, grade)
        );
    }

    /**
     * 获取活动表中所有不重复的年份
     */
    public List<String> getActivityYears() {
        List<Activity> all = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>().select(Activity::getGrade).groupBy(Activity::getGrade)
        );
        return all.stream().map(Activity::getGrade).sorted(Comparator.reverseOrder()).collect(Collectors.toList());
    }

    /**
     * 获取指定年份的活动数量
     */
    public long getCountByGrade(String grade) {
        return activityMapper.selectCount(
                new LambdaQueryWrapper<Activity>().eq(Activity::getGrade, grade)
        );
    }
}
