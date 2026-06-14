package com.sap.service;

import com.sap.BaseUnitTest;
import com.sap.entity.Position;
import com.sap.entity.User;
import com.sap.mapper.PositionMapper;
import com.sap.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CacheServiceTest extends BaseUnitTest {

    @Mock UserMapper userMapper;
    @Mock PositionMapper positionMapper;

    @InjectMocks CacheService service;

    private User user(long id, String studentId, String name, String nickname) {
        User u = new User();
        u.setId(id);
        u.setStudentId(studentId);
        u.setName(name);
        u.setNickname(nickname);
        return u;
    }

    private Position position(int id, String name, Integer roleCode) {
        Position p = new Position();
        p.setId(id);
        p.setPositionName(name);
        p.setRoleCode(roleCode);
        return p;
    }

    // ===================== init =====================

    @Test
    void init_loadsUsersAndPositions() {
        when(userMapper.selectList(null)).thenReturn(List.of(user(1, "S1", "Alice", "al")));
        when(positionMapper.selectList(null)).thenReturn(List.of(position(1, "会长", 1)));

        service.init();

        assertEquals("Alice", service.getUserName(1L));
        assertEquals("会长", service.getPositionName(1));
    }

    // ===================== refreshUsers =====================

    @Test
    void refreshUsers_populatesBothIndexes_skipsNullStudentId() {
        when(userMapper.selectList(null)).thenReturn(Arrays.asList(
                user(1, "S1", "Alice", "al"),
                user(2, null, "Bob", "bo")));

        service.refreshUsers();

        assertEquals("Alice", service.getUserById(1L).getName());
        assertEquals("Alice", service.getUserByStudentId("S1").getName());
        assertNull(service.getUserByStudentId("S2"));
        // user with null studentId still indexed by id
        assertEquals("Bob", service.getUserById(2L).getName());
    }

    @Test
    void refreshUsers_clearsStaleEntries() {
        when(userMapper.selectList(null)).thenReturn(List.of(user(1, "S1", "Alice", "al")));
        service.refreshUsers();
        assertNotNull(service.getUserById(1L));

        when(userMapper.selectList(null)).thenReturn(List.of(user(2, "S2", "Bob", "bo")));
        service.refreshUsers();
        assertNull(service.getUserById(1L), "旧用户应被清除");
        assertNotNull(service.getUserById(2L));
    }

    // ===================== getUserById =====================

    @Test
    void getUserById_nullId_returnsNull() {
        assertNull(service.getUserById(null));
    }

    @Test
    void getUserById_missing_returnsNull() {
        assertNull(service.getUserById(404L));
    }

    // ===================== getUserByStudentId =====================

    @Test
    void getUserByStudentId_nullArg_returnsNull() {
        assertNull(service.getUserByStudentId(null));
    }

    // ===================== getUserName =====================

    @Test
    void getUserName_present_returnsName() {
        service.addUser(user(5, "S5", "Eve", "ev"));
        assertEquals("Eve", service.getUserName(5L));
    }

    @Test
    void getUserName_missing_returnsUnknown() {
        assertEquals("未知", service.getUserName(999L));
    }

    // ===================== getStudentId =====================

    @Test
    void getStudentId_present_returnsStudentId() {
        service.addUser(user(6, "S6", "Frank", "fr"));
        assertEquals("S6", service.getStudentId(6L));
    }

    @Test
    void getStudentId_missing_returnsEmpty() {
        assertEquals("", service.getStudentId(888L));
    }

    // ===================== getAllUsers =====================

    @Test
    void getAllUsers_returnsSnapshot() {
        service.addUser(user(1, "S1", "A", "a"));
        service.addUser(user(2, "S2", "B", "b"));
        assertEquals(2, service.getAllUsers().size());
    }

    // ===================== searchUsers =====================

    @Test
    void searchUsers_nullKeyword_returnsAll() {
        service.addUser(user(1, "S1", "A", "a"));
        assertEquals(1, service.searchUsers(null).size());
    }

    @Test
    void searchUsers_emptyKeyword_returnsAll() {
        service.addUser(user(1, "S1", "A", "a"));
        assertEquals(1, service.searchUsers("").size());
    }

    @Test
    void searchUsers_matchesByNameStudentIdNickname_caseInsensitive() {
        service.addUser(user(1, "2024001", "Alice", "lily"));
        service.addUser(user(2, "2024002", "Bob", "bobby"));
        service.addUser(user(3, null, null, null)); // all-null fields, never matches

        assertEquals(1, service.searchUsers("alice").size());
        assertEquals(1, service.searchUsers("2024002").size());
        assertEquals(1, service.searchUsers("LILY").size());
        assertEquals(0, service.searchUsers("nomatch").size());
    }

    // ===================== addUser / updateUser =====================

    @Test
    void addUser_indexesByIdAndStudentId() {
        service.addUser(user(10, "S10", "Ten", "t"));
        assertEquals("Ten", service.getUserById(10L).getName());
        assertEquals("Ten", service.getUserByStudentId("S10").getName());
    }

    @Test
    void addUser_nullStudentId_onlyIndexedById() {
        service.addUser(user(11, null, "Eleven", "e"));
        assertNotNull(service.getUserById(11L));
        assertNull(service.getUserByStudentId("anything"));
    }

    @Test
    void updateUser_delegatesToAddUser() {
        service.updateUser(user(12, "S12", "Twelve", "tw"));
        assertEquals("Twelve", service.getUserById(12L).getName());
    }

    // ===================== refreshPositions =====================

    @Test
    void refreshPositions_populatesAndClearsStale() {
        when(positionMapper.selectList(null)).thenReturn(List.of(position(1, "会长", 1)));
        service.refreshPositions();
        assertEquals("会长", service.getPositionById(1).getPositionName());

        when(positionMapper.selectList(null)).thenReturn(List.of(position(2, "部长", 2)));
        service.refreshPositions();
        assertNull(service.getPositionById(1));
        assertEquals("部长", service.getPositionById(2).getPositionName());
    }

    // ===================== getPositionById =====================

    @Test
    void getPositionById_nullId_returnsNull() {
        assertNull(service.getPositionById(null));
    }

    // ===================== getPositionName =====================

    @Test
    void getPositionName_present_returnsName() {
        when(positionMapper.selectList(null)).thenReturn(List.of(position(1, "会长", 1)));
        service.refreshPositions();
        assertEquals("会长", service.getPositionName(1));
    }

    @Test
    void getPositionName_missing_returnsUnknown() {
        assertEquals("未知", service.getPositionName(777));
    }

    // ===================== getAllPositions =====================

    @Test
    void getAllPositions_returnsSnapshot() {
        when(positionMapper.selectList(null))
                .thenReturn(List.of(position(1, "会长", 1), position(2, "部长", 2)));
        service.refreshPositions();
        assertEquals(2, service.getAllPositions().size());
    }

    // ===================== getAdminPositions =====================

    @Test
    void getAdminPositions_filtersByRoleCodeLeq2_andSkipsNullRoleCode() {
        when(positionMapper.selectList(null)).thenReturn(List.of(
                position(1, "超管", 0),
                position(2, "部长", 2),
                position(3, "成员", 3),
                position(4, "无角色", null)));
        service.refreshPositions();

        List<Position> admins = service.getAdminPositions();
        assertEquals(2, admins.size());
        assertTrue(admins.stream().allMatch(p -> p.getRoleCode() <= 2));
    }

    // ===================== getPositionByName =====================

    @Test
    void getPositionByName_found() {
        when(positionMapper.selectList(null)).thenReturn(List.of(position(1, "成员", 3)));
        service.refreshPositions();
        assertEquals(1, service.getPositionByName("成员").getId());
    }

    @Test
    void getPositionByName_notFound_returnsNull() {
        when(positionMapper.selectList(null)).thenReturn(List.of(position(1, "成员", 3)));
        service.refreshPositions();
        assertNull(service.getPositionByName("不存在"));
    }
}
