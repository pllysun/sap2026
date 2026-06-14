package com.sap.service;

import com.sap.BaseUnitTest;
import com.sap.common.BusinessException;
import com.sap.entity.OutstandingMember;
import com.sap.mapper.OutstandingMemberMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutstandingMemberServiceTest extends BaseUnitTest {

    @Mock OutstandingMemberMapper mapper;

    @InjectMocks OutstandingMemberService service;

    private OutstandingMember member(long id) {
        OutstandingMember m = new OutstandingMember();
        m.setId(id);
        m.setName("n" + id);
        m.setGender("男");
        m.setGrade("2025");
        m.setMajor("CS");
        m.setDestination("考研");
        m.setDestinationDetail("某校");
        m.setBio("bio");
        return m;
    }

    @Test
    void listAll_delegatesToMapper() {
        List<OutstandingMember> data = List.of(member(1));
        when(mapper.selectList(any())).thenReturn(data);
        assertEquals(data, service.listAll());
    }

    @Test
    void listByGrade_delegatesToMapper() {
        List<OutstandingMember> data = List.of(member(2));
        when(mapper.selectList(any())).thenReturn(data);
        assertEquals(data, service.listByGrade("2025"));
    }

    @Test
    void add_insertsMember() {
        OutstandingMember m = member(0);
        service.add(m);
        verify(mapper).insert(m);
    }

    @Test
    void update_copiesFieldsAndUpdates() {
        OutstandingMember existing = member(5);
        when(mapper.selectById(5L)).thenReturn(existing);

        OutstandingMember changed = new OutstandingMember();
        changed.setName("newName");
        changed.setGender("女");
        changed.setGrade("2026");
        changed.setMajor("Math");
        changed.setDestination("就业");
        changed.setDestinationDetail("某公司");
        changed.setBio("newBio");

        service.update(5L, changed);

        ArgumentCaptor<OutstandingMember> captor = ArgumentCaptor.forClass(OutstandingMember.class);
        verify(mapper).updateById(captor.capture());
        OutstandingMember saved = captor.getValue();
        assertEquals("newName", saved.getName());
        assertEquals("女", saved.getGender());
        assertEquals("2026", saved.getGrade());
        assertEquals("Math", saved.getMajor());
        assertEquals("就业", saved.getDestination());
        assertEquals("某公司", saved.getDestinationDetail());
        assertEquals("newBio", saved.getBio());
    }

    @Test
    void update_notFound_throws() {
        when(mapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(99L, new OutstandingMember()));
        assertEquals("记录不存在", ex.getMessage());
    }

    @Test
    void delete_deletesById() {
        service.delete(7L);
        verify(mapper).deleteById(7L);
    }

    @Test
    void count_delegatesToMapper() {
        when(mapper.selectCount(isNull())).thenReturn(12L);
        assertEquals(12L, service.count());
    }
}
