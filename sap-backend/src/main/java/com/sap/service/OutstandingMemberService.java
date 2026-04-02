package com.sap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sap.common.BusinessException;
import com.sap.entity.OutstandingMember;
import com.sap.mapper.OutstandingMemberMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OutstandingMemberService {

    @Autowired
    private OutstandingMemberMapper mapper;

    public List<OutstandingMember> listAll() {
        return mapper.selectList(
                new LambdaQueryWrapper<OutstandingMember>()
                        .orderByDesc(OutstandingMember::getGrade)
                        .orderByDesc(OutstandingMember::getCreatedAt)
        );
    }

    public List<OutstandingMember> listByGrade(String grade) {
        return mapper.selectList(
                new LambdaQueryWrapper<OutstandingMember>()
                        .eq(OutstandingMember::getGrade, grade)
                        .orderByDesc(OutstandingMember::getCreatedAt)
        );
    }

    public void add(OutstandingMember member) {
        mapper.insert(member);
    }

    public void update(Long id, OutstandingMember member) {
        OutstandingMember existing = mapper.selectById(id);
        if (existing == null) throw new BusinessException("记录不存在");
        existing.setName(member.getName());
        existing.setGender(member.getGender());
        existing.setGrade(member.getGrade());
        existing.setMajor(member.getMajor());
        existing.setDestination(member.getDestination());
        existing.setDestinationDetail(member.getDestinationDetail());
        existing.setBio(member.getBio());
        mapper.updateById(existing);
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }

    public long count() {
        return mapper.selectCount(null);
    }
}
