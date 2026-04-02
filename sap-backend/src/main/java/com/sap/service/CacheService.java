package com.sap.service;

import com.sap.entity.Position;
import com.sap.entity.User;
import com.sap.mapper.PositionMapper;
import com.sap.mapper.UserMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 用户与身份内存缓存服务
 * <p>启动时加载全部用户和身份到内存，提供高速查询。
 * 数据量小（十年1000人以内），全量内存操作效率远高于数据库。</p>
 */
@Service
public class CacheService {

    @Autowired private UserMapper userMapper;
    @Autowired private PositionMapper positionMapper;

    /** 用户缓存：id -> User */
    private final Map<Long, User> userById = new ConcurrentHashMap<>();
    /** 用户缓存：studentId -> User */
    private final Map<String, User> userByStudentId = new ConcurrentHashMap<>();
    /** 身份缓存：id -> Position */
    private final Map<Integer, Position> positionById = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refreshUsers();
        refreshPositions();
    }

    // ============= 用户缓存 =============

    public void refreshUsers() {
        List<User> all = userMapper.selectList(null);
        userById.clear();
        userByStudentId.clear();
        for (User u : all) {
            userById.put(u.getId(), u);
            if (u.getStudentId() != null) {
                userByStudentId.put(u.getStudentId(), u);
            }
        }
    }

    public User getUserById(Long id) {
        return id == null ? null : userById.get(id);
    }

    public User getUserByStudentId(String studentId) {
        return studentId == null ? null : userByStudentId.get(studentId);
    }

    public String getUserName(Long id) {
        User u = getUserById(id);
        return u != null ? u.getName() : "未知";
    }

    public String getStudentId(Long id) {
        User u = getUserById(id);
        return u != null ? u.getStudentId() : "";
    }

    public List<User> getAllUsers() {
        return new ArrayList<>(userById.values());
    }

    public List<User> searchUsers(String keyword) {
        if (keyword == null || keyword.isEmpty()) return getAllUsers();
        String kw = keyword.toLowerCase();
        return userById.values().stream()
                .filter(u -> (u.getName() != null && u.getName().toLowerCase().contains(kw))
                        || (u.getStudentId() != null && u.getStudentId().toLowerCase().contains(kw))
                        || (u.getNickname() != null && u.getNickname().toLowerCase().contains(kw)))
                .collect(Collectors.toList());
    }

    /** 新增用户后刷新缓存 */
    public void addUser(User user) {
        userById.put(user.getId(), user);
        if (user.getStudentId() != null) {
            userByStudentId.put(user.getStudentId(), user);
        }
    }

    /** 更新用户后刷新缓存 */
    public void updateUser(User user) {
        addUser(user);
    }

    // ============= 身份缓存 =============

    public void refreshPositions() {
        List<Position> all = positionMapper.selectList(null);
        positionById.clear();
        for (Position p : all) {
            positionById.put(p.getId(), p);
        }
    }

    public Position getPositionById(Integer id) {
        return id == null ? null : positionById.get(id);
    }

    public String getPositionName(Integer id) {
        Position p = getPositionById(id);
        return p != null ? p.getPositionName() : "未知";
    }

    public List<Position> getAllPositions() {
        return new ArrayList<>(positionById.values());
    }

    /** 获取管理层身份（roleCode <= 2） */
    public List<Position> getAdminPositions() {
        return positionById.values().stream()
                .filter(p -> p.getRoleCode() != null && p.getRoleCode() <= 2)
                .collect(Collectors.toList());
    }

    /** 获取指定身份名的Position */
    public Position getPositionByName(String name) {
        return positionById.values().stream()
                .filter(p -> name.equals(p.getPositionName()))
                .findFirst().orElse(null);
    }
}
