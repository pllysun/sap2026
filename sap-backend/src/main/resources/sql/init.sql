-- ============================================
-- 软件协会社团管理系统 数据库初始化脚本
-- ============================================

CREATE DATABASE IF NOT EXISTS sap_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE sap_db;

-- 1. 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id VARCHAR(20) NOT NULL UNIQUE COMMENT '学号',
    password VARCHAR(255) NOT NULL COMMENT '密码(BCrypt)',
    name VARCHAR(50) NOT NULL COMMENT '姓名',
    nickname VARCHAR(50) COMMENT '网名(默认姓名)',
    gender TINYINT COMMENT '性别 0女 1男',
    qq VARCHAR(20) NOT NULL COMMENT 'QQ号',
    grade VARCHAR(10) COMMENT '年级',
    avatar VARCHAR(255) DEFAULT '/default-avatar.png' COMMENT '头像',
    status TINYINT DEFAULT 1 COMMENT '状态 0禁用 1正常',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_student_id (student_id),
    INDEX idx_grade (grade)
) COMMENT '用户表';

-- 2. 权限表
CREATE TABLE IF NOT EXISTS sys_role (
    id INT PRIMARY KEY AUTO_INCREMENT,
    role_code INT NOT NULL UNIQUE COMMENT '权限码',
    role_name VARCHAR(50) NOT NULL COMMENT '权限名称'
) COMMENT '权限表';

INSERT INTO sys_role (role_code, role_name) VALUES
(0, '超级管理员'), (1, '会长'), (2, '管理员'), (3, '成员'), (4, '游客');

-- 3. 用户权限关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_code INT NOT NULL,
    UNIQUE KEY uk_user_role (user_id, role_code),
    INDEX idx_user_id (user_id)
) COMMENT '用户权限关联表';

-- 4. 设置表
CREATE TABLE IF NOT EXISTS sys_setting (
    id INT PRIMARY KEY AUTO_INCREMENT,
    setting_key VARCHAR(100) NOT NULL UNIQUE COMMENT '设置键',
    setting_value VARCHAR(500) NOT NULL COMMENT '设置值',
    description VARCHAR(255) COMMENT '描述',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '设置表';

INSERT INTO sys_setting (setting_key, setting_value, description) VALUES
('current_grade', '2025', '当前年级');

INSERT IGNORE INTO sys_setting (setting_key, setting_value, description) VALUES
('footer_address', '中南林业科技大学 学生活动中心1701', '页脚-地址'),
('footer_qq', '1576316531', '页脚-官方QQ'),
('footer_email', 'sap@csuft.edu.cn', '页脚-联系邮箱'),
('footer_copyright', '中南林业科技大学软件协会', '页脚-版权主体名称'),
('qr_qq_group_url', '', 'QQ群二维码图片URL'),
('qr_qq_group_name', '', 'QQ群二维码名称'),
('qr_qq_account_url', '', 'QQ号二维码图片URL'),
('qr_qq_account_name', '', 'QQ号二维码名称');

-- 5. 身份表
CREATE TABLE IF NOT EXISTS sys_position (
    id INT PRIMARY KEY AUTO_INCREMENT,
    position_name VARCHAR(50) NOT NULL COMMENT '身份名称',
    is_system TINYINT DEFAULT 0 COMMENT '系统内置(不可删改)',
    sort_order INT DEFAULT 0 COMMENT '排序',
    max_count INT DEFAULT 1 COMMENT '最大人数',
    role_code INT DEFAULT 3 COMMENT '对应权限码',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除'
) COMMENT '身份表';

INSERT INTO sys_position (position_name, is_system, sort_order, max_count, role_code) VALUES
('会长', 1, 1, 1, 1),
('团支书', 1, 2, 1, 2),
('副会长', 0, 3, 2, 2),
('学术部部长', 0, 4, 1, 2),
('学术部副部长', 0, 5, 2, 2),
('宣传部部长', 0, 6, 1, 2),
('宣传部副部长', 0, 7, 2, 2),
('成员', 0, 99, 999, 3);

-- 6. 换届表
CREATE TABLE IF NOT EXISTS sys_term (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    grade VARCHAR(10) NOT NULL COMMENT '年级',
    position_id INT NOT NULL COMMENT '身份ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_user_grade_pos (user_id, grade, position_id),
    INDEX idx_grade (grade)
) COMMENT '换届表';

-- 7. 活动表
CREATE TABLE IF NOT EXISTS act_activity (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    grade VARCHAR(10) NOT NULL COMMENT '年份',
    seq_num INT NOT NULL COMMENT '活动次数',
    title VARCHAR(200) NOT NULL COMMENT '活动名称',
    content TEXT COMMENT '活动内容',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_grade_seq (grade, seq_num),
    INDEX idx_grade (grade)
) COMMENT '活动表';

-- 8. 活动图片表
CREATE TABLE IF NOT EXISTS act_activity_image (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    image_url VARCHAR(500) NOT NULL COMMENT '图片地址',
    sort_order INT DEFAULT 0 COMMENT '排序',
    INDEX idx_activity_id (activity_id)
) COMMENT '活动图片表';

-- 9. 财务表
CREATE TABLE IF NOT EXISTS fin_bill (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    bill_type TINYINT NOT NULL COMMENT '0支出 1收入',
    content VARCHAR(500) NOT NULL COMMENT '账单内容',
    amount DECIMAL(10,2) NOT NULL COMMENT '金额',
    bill_time DATETIME NOT NULL COMMENT '消费/收入时间',
    remark VARCHAR(500) COMMENT '备注',
    grade VARCHAR(10) COMMENT '活动年级',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_grade (grade),
    INDEX idx_bill_type (bill_type)
) COMMENT '财务表';

-- 10. 财务图片表
CREATE TABLE IF NOT EXISTS fin_bill_image (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    bill_id BIGINT NOT NULL,
    image_url VARCHAR(500) NOT NULL COMMENT '图片地址',
    INDEX idx_bill_id (bill_id)
) COMMENT '财务图片表';

-- 11. 学习小组活动表
CREATE TABLE IF NOT EXISTS study_activity (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    grade VARCHAR(10) NOT NULL COMMENT '年级',
    seq_num INT NOT NULL COMMENT '活动次数',
    current_week INT DEFAULT 1 COMMENT '当前周期',
    total_weeks INT DEFAULT 4 COMMENT '总周期数',
    title VARCHAR(200) COMMENT '活动标题',
    status TINYINT DEFAULT 1 COMMENT '0关闭 1进行中',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_grade_seq (grade, seq_num),
    INDEX idx_grade (grade)
) COMMENT '学习小组活动表';

-- 12. 学习小组负责人表
CREATE TABLE IF NOT EXISTS study_leader (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_id BIGINT NOT NULL COMMENT '学习活动ID',
    user_id BIGINT NOT NULL COMMENT '负责人用户ID',
    student_id VARCHAR(20) NOT NULL COMMENT '负责人学号',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_activity_id (activity_id)
) COMMENT '学习小组负责人表';

-- 13. 学习成员表
CREATE TABLE IF NOT EXISTS study_member (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_id BIGINT NOT NULL COMMENT '学习活动ID',
    user_id BIGINT NOT NULL COMMENT '成员用户ID',
    leader_id BIGINT COMMENT '分配的负责人ID',
    week INT NOT NULL COMMENT '所属周期',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_activity_id (activity_id),
    INDEX idx_leader_id (leader_id)
) COMMENT '学习成员表';

-- 14. 学习评分表
CREATE TABLE IF NOT EXISTS study_score (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_id BIGINT NOT NULL COMMENT '学习活动ID',
    week INT NOT NULL COMMENT '周期',
    member_user_id BIGINT NOT NULL COMMENT '被评分成员ID',
    leader_user_id BIGINT NOT NULL COMMENT '评分负责人ID',
    score INT NOT NULL COMMENT '分数 1-10',
    comment VARCHAR(1000) NOT NULL COMMENT '评语',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_activity_week_member (activity_id, week, member_user_id),
    INDEX idx_activity_id (activity_id)
) COMMENT '学习评分表';

-- 15. 学习资料上传表
CREATE TABLE IF NOT EXISTS study_material (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_id BIGINT NOT NULL COMMENT '学习活动ID',
    week INT NOT NULL COMMENT '周期',
    user_id BIGINT NOT NULL COMMENT '上传者ID',
    file_type TINYINT NOT NULL COMMENT '0学习资料 1作业',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名',
    file_url VARCHAR(500) NOT NULL COMMENT '文件地址',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_activity_week (activity_id, week)
) COMMENT '学习资料上传表';

-- 16. 留言板
CREATE TABLE IF NOT EXISTS msg_board (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT COMMENT '用户ID(可匿名)',
    content TEXT NOT NULL COMMENT '留言内容',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除'
) COMMENT '留言板';

-- 17. 入会负责人
CREATE TABLE IF NOT EXISTS join_manager (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '负责人用户ID',
    grade VARCHAR(10) NOT NULL COMMENT '负责年级',
    alipay_qr VARCHAR(500) COMMENT '支付宝收款码URL',
    wechat_qr VARCHAR(500) COMMENT '微信收款码URL',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_grade (user_id, grade)
) COMMENT '入会负责人';

-- 18. 入会申请
CREATE TABLE IF NOT EXISTS join_application (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '申请人ID',
    manager_id BIGINT COMMENT '分配的负责人user_id',
    payment_code VARCHAR(100) COMMENT '支付编码',
    status TINYINT DEFAULT 0 COMMENT '0待提交 1已提交 2已通过',
    assigned_at DATETIME COMMENT '分配负责人时间',
    submitted_at DATETIME COMMENT '提交支付码时间',
    approved_at DATETIME COMMENT '审核通过时间',
    approved_by BIGINT COMMENT '审核人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) COMMENT '入会申请';

-- ============================================
-- 初始管理员账号 (学号: admin, 密码: admin123)
-- BCrypt 加密后的密码需要程序启动后插入
-- ============================================
