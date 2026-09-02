package com.sap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sap.common.BusinessException;
import com.sap.dto.AppAnnouncementDTO;
import com.sap.entity.AppAnnouncement;
import com.sap.mapper.AppAnnouncementMapper;
import com.sap.vo.AppAnnouncementVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AppAnnouncementService {

    private final AppAnnouncementMapper mapper;

    public AppAnnouncementService(AppAnnouncementMapper mapper) {
        this.mapper = mapper;
    }

    public List<AppAnnouncementVO> publishedList() {
        return mapper.selectList(new LambdaQueryWrapper<AppAnnouncement>()
                        .eq(AppAnnouncement::getPublished, true)
                        .orderByDesc(AppAnnouncement::getUpdatedAt)
                        .orderByDesc(AppAnnouncement::getId))
                .stream().map(this::toVO).toList();
    }

    public List<AppAnnouncementVO> adminList() {
        return mapper.selectList(new LambdaQueryWrapper<AppAnnouncement>()
                        .orderByDesc(AppAnnouncement::getUpdatedAt)
                        .orderByDesc(AppAnnouncement::getId))
                .stream().map(this::toVO).toList();
    }

    public AppAnnouncementVO create(long operatorId, AppAnnouncementDTO dto) {
        AppAnnouncement item = new AppAnnouncement();
        apply(item, dto);
        item.setCreatedBy(operatorId);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        item.setDeleted(0);
        mapper.insert(item);
        return toVO(item);
    }

    public AppAnnouncementVO update(Long id, AppAnnouncementDTO dto) {
        AppAnnouncement item = required(id);
        apply(item, dto);
        item.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(item);
        return toVO(item);
    }

    public void delete(Long id) {
        required(id);
        mapper.deleteById(id);
    }

    private AppAnnouncement required(Long id) {
        AppAnnouncement item = id == null ? null : mapper.selectById(id);
        if (item == null) throw new BusinessException(404, "公告不存在");
        return item;
    }

    private void apply(AppAnnouncement item, AppAnnouncementDTO dto) {
        String title = dto.getTitle() == null ? "" : dto.getTitle().trim();
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (title.isEmpty() || title.length() > 120) {
            throw new BusinessException(400, "公告标题须为 1～120 个字符");
        }
        if (content.isEmpty() || content.length() > 10000) {
            throw new BusinessException(400, "公告内容须为 1～10000 个字符");
        }
        item.setTitle(title);
        item.setContent(content);
        item.setPublished(!Boolean.FALSE.equals(dto.getPublished()));
    }

    private AppAnnouncementVO toVO(AppAnnouncement item) {
        AppAnnouncementVO vo = new AppAnnouncementVO();
        vo.setId(item.getId());
        vo.setTitle(item.getTitle());
        vo.setContent(item.getContent());
        vo.setPublished(item.getPublished());
        vo.setCreatedAt(item.getCreatedAt());
        vo.setUpdatedAt(item.getUpdatedAt());
        return vo;
    }
}
