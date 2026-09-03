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
('qr_qq_account_name', '', 'QQ号二维码名称'),
('guest_access_level', '0', '软协课表游客权限等级：0关闭、1基础、2完整App能力');

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

-- 19. 软协课表意见反馈 Issue
CREATE TABLE IF NOT EXISTS app_feedback_issue (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reporter_id BIGINT NOT NULL COMMENT '反馈账号ID',
    title VARCHAR(120) NOT NULL COMMENT '标题',
    content MEDIUMTEXT NOT NULL COMMENT '反馈正文',
    image_urls TEXT COMMENT '反馈图片COS URL JSON数组，最多4张',
    category VARCHAR(24) NOT NULL DEFAULT 'OTHER' COMMENT 'BUG/FEATURE/EXPERIENCE/OTHER',
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/CLOSED',
    app_version_name VARCHAR(32) COMMENT '反馈时App版本名',
    app_version_code INT COMMENT '反馈时App构建号',
    device_info VARCHAR(255) COMMENT '旧版兼容列，新反馈不再保存运行环境',
    closed_by BIGINT COMMENT '关闭操作人ID',
    closed_at DATETIME COMMENT '关闭时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_feedback_status_updated (status, updated_at),
    INDEX idx_feedback_reporter (reporter_id),
    INDEX idx_feedback_reporter_pending (reporter_id, status, deleted)
) COMMENT '软协课表意见反馈Issue';

-- 20. 软协课表意见反馈时间线回复
CREATE TABLE IF NOT EXISTS app_feedback_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    issue_id BIGINT NOT NULL COMMENT 'Issue ID',
    author_id BIGINT NOT NULL COMMENT '回复人ID',
    parent_id BIGINT COMMENT '一级回复ID，NULL为根回复',
    content TEXT NOT NULL COMMENT '回复内容',
    admin_reply TINYINT NOT NULL DEFAULT 0 COMMENT '是否指定维护者账号回复',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_feedback_comment_issue (issue_id, created_at),
    INDEX idx_feedback_comment_parent (issue_id, parent_id, created_at),
    INDEX idx_feedback_comment_handled (issue_id, admin_reply, deleted)
) COMMENT '软协课表意见反馈回复';

-- 21. 软协课表公告
CREATE TABLE IF NOT EXISTS app_announcement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(120) NOT NULL COMMENT '公告标题',
    content MEDIUMTEXT NOT NULL COMMENT '公告正文',
    published TINYINT NOT NULL DEFAULT 1 COMMENT '是否在App展示',
    created_by BIGINT NOT NULL COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_app_announcement_published (published, updated_at)
) COMMENT '软协课表公告';

-- 22. 班级课表采集：四个来源表分开保存，查询时以班级表为主关联补全
CREATE TABLE IF NOT EXISTS jw_class_schedule_term (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    term_value VARCHAR(32) NOT NULL UNIQUE,
    term_label VARCHAR(80),
    semester_start_date VARCHAR(16),
    row_count INT DEFAULT 0,
    class_count INT DEFAULT 0,
    teacher_count INT DEFAULT 0,
    room_count INT DEFAULT 0,
    course_count INT DEFAULT 0,
    last_collected_at DATETIME,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 班级、教师、教室、课程四个 *_ifr 接口的统一字段；raw_json 用于后续适配新字段。
CREATE TABLE IF NOT EXISTS jw_class_schedule_class (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    term_value VARCHAR(32) NOT NULL,
    source_key VARCHAR(96) NOT NULL,
    external_id VARCHAR(96),
    course_no VARCHAR(64), course_name VARCHAR(255), week_range VARCHAR(128),
    weekday VARCHAR(32), section VARCHAR(32), room_name VARCHAR(255), teacher_name VARCHAR(1024),
    college VARCHAR(255), grade VARCHAR(32), major VARCHAR(255), class_name VARCHAR(1024),
    course_dept VARCHAR(255), category VARCHAR(128), course_attr VARCHAR(128), course_nature VARCHAR(128),
    exam_method VARCHAR(128), group_name VARCHAR(255), hours DECIMAL(10,2), credit DECIMAL(10,2),
    raw_json LONGTEXT,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_jw_class_schedule_class_term (term_value),
    INDEX idx_jw_class_schedule_class_class (term_value, college, major, class_name(191)),
    INDEX idx_jw_class_schedule_class_filter (term_value, college, grade, major, class_name(191)),
    INDEX idx_jw_class_schedule_class_join (term_value, external_id, weekday, section, week_range),
    UNIQUE (term_value, source_key)
);

CREATE TABLE IF NOT EXISTS jw_class_schedule_teacher (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    term_value VARCHAR(32) NOT NULL,
    source_key VARCHAR(96) NOT NULL,
    external_id VARCHAR(96),
    course_no VARCHAR(64), course_name VARCHAR(255), week_range VARCHAR(128),
    weekday VARCHAR(32), section VARCHAR(32), room_name VARCHAR(255), teacher_name VARCHAR(1024),
    college VARCHAR(255), grade VARCHAR(32), major VARCHAR(255), class_name VARCHAR(1024),
    course_dept VARCHAR(255), category VARCHAR(128), course_attr VARCHAR(128), course_nature VARCHAR(128),
    exam_method VARCHAR(128), group_name VARCHAR(255), hours DECIMAL(10,2), credit DECIMAL(10,2),
    raw_json LONGTEXT,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_jw_class_schedule_teacher_term (term_value),
    INDEX idx_jw_class_schedule_teacher_class (term_value, college, major, class_name(191)),
    INDEX idx_jw_class_schedule_teacher_filter (term_value, college, grade, major, class_name(191)),
    INDEX idx_jw_class_schedule_teacher_join (term_value, external_id, weekday, section, week_range),
    UNIQUE (term_value, source_key)
);

CREATE TABLE IF NOT EXISTS jw_class_schedule_room (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    term_value VARCHAR(32) NOT NULL,
    source_key VARCHAR(96) NOT NULL,
    external_id VARCHAR(96),
    course_no VARCHAR(64), course_name VARCHAR(255), week_range VARCHAR(128),
    weekday VARCHAR(32), section VARCHAR(32), room_name VARCHAR(255), teacher_name VARCHAR(1024),
    college VARCHAR(255), grade VARCHAR(32), major VARCHAR(255), class_name VARCHAR(1024),
    course_dept VARCHAR(255), category VARCHAR(128), course_attr VARCHAR(128), course_nature VARCHAR(128),
    exam_method VARCHAR(128), group_name VARCHAR(255), hours DECIMAL(10,2), credit DECIMAL(10,2),
    raw_json LONGTEXT,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_jw_class_schedule_room_term (term_value),
    INDEX idx_jw_class_schedule_room_class (term_value, college, major, class_name(191)),
    INDEX idx_jw_class_schedule_room_filter (term_value, college, grade, major, class_name(191)),
    INDEX idx_jw_class_schedule_room_join (term_value, external_id, weekday, section, week_range),
    UNIQUE (term_value, source_key)
);

CREATE TABLE IF NOT EXISTS jw_class_schedule_course (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    term_value VARCHAR(32) NOT NULL,
    source_key VARCHAR(96) NOT NULL,
    external_id VARCHAR(96),
    course_no VARCHAR(64), course_name VARCHAR(255), week_range VARCHAR(128),
    weekday VARCHAR(32), section VARCHAR(32), room_name VARCHAR(255), teacher_name VARCHAR(1024),
    college VARCHAR(255), grade VARCHAR(32), major VARCHAR(255), class_name VARCHAR(1024),
    course_dept VARCHAR(255), category VARCHAR(128), course_attr VARCHAR(128), course_nature VARCHAR(128),
    exam_method VARCHAR(128), group_name VARCHAR(255), hours DECIMAL(10,2), credit DECIMAL(10,2),
    raw_json LONGTEXT,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_jw_class_schedule_course_term (term_value),
    INDEX idx_jw_class_schedule_course_class (term_value, college, major, class_name(191)),
    INDEX idx_jw_class_schedule_course_filter (term_value, college, grade, major, class_name(191)),
    INDEX idx_jw_class_schedule_course_join (term_value, external_id, weekday, section, week_range),
    UNIQUE (term_value, source_key)
);

CREATE TABLE IF NOT EXISTS jw_class_schedule_pull_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id VARCHAR(64) NOT NULL,
    term_value VARCHAR(32), source_type VARCHAR(20), trigger_type VARCHAR(20) NOT NULL,
    actor_id BIGINT, actor_name VARCHAR(80), status VARCHAR(20) NOT NULL,
    message VARCHAR(1000), row_count INT DEFAULT 0,
    started_at DATETIME DEFAULT CURRENT_TIMESTAMP, finished_at DATETIME,
    INDEX idx_pull_batch (batch_id), INDEX idx_pull_time (started_at)
);

CREATE TABLE IF NOT EXISTS jw_class_schedule_pull_schedule (
    id TINYINT PRIMARY KEY,
    enabled TINYINT NOT NULL DEFAULT 0,
    schedule_type VARCHAR(16) NOT NULL DEFAULT 'DAILY',
    hour_num TINYINT NOT NULL DEFAULT 0, minute_num TINYINT NOT NULL DEFAULT 0,
    day_of_week TINYINT, day_of_month TINYINT, run_at DATETIME,
    owner_user_id BIGINT, jw_account VARCHAR(32), jw_password_enc VARCHAR(512), created_by BIGINT, updated_by BIGINT, last_run_key VARCHAR(80),
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
INSERT IGNORE INTO jw_class_schedule_pull_schedule (id) VALUES (1);

-- ============================================
-- 初始管理员账号
-- 由后端启动时的数据初始化逻辑（DataInitializer）自动创建，
-- 密码经 BCrypt 加密后写入，不在此处以明文形式记录。
-- 首次登录后请立即修改默认密码。
-- ============================================
