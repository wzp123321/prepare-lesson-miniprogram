-- ============================================================
-- 备课排课管理系统 数据库初始化脚本 (MySQL 8)
-- 字符集 utf8mb4 / 引擎 InnoDB
-- 连接信息见 backend/src/main/resources/application.yml
--   ⚠️ Q17 待确认：库名 / 账号 / 密码 由用户补充（占位不阻塞）。
--   执行前请先 CREATE DATABASE lesson_db CHARACTER SET utf8mb4;
-- ============================================================
SET NAMES utf8mb4;

-- 学生（聚合根）
CREATE TABLE IF NOT EXISTS student (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    name            VARCHAR(50)   NOT NULL                COMMENT '姓名',
    grade           VARCHAR(50)   DEFAULT NULL            COMMENT '年级（取自 dict.grade）',
    phone           VARCHAR(30)   DEFAULT NULL            COMMENT '电话',
    parent_wechat   VARCHAR(100)  DEFAULT NULL            COMMENT '家长微信',
    address         VARCHAR(255)  NOT NULL                COMMENT '家庭地址',
    price           DECIMAL(10,2) NOT NULL DEFAULT 0.00  COMMENT '课程价格 元/节（月收入统计用）',
    status          TINYINT       NOT NULL DEFAULT 1      COMMENT '在读状态 1 在读 / 0 暂停',
    color           VARCHAR(20)   DEFAULT NULL            COMMENT '专属颜色 hex（课表着色）',
    remark          VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP                     COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_student_grade (grade),
    KEY idx_student_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生';

-- 时间段（排课网格的列）
CREATE TABLE IF NOT EXISTS time_slot (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    start_time  TIME        DEFAULT NULL            COMMENT '起',
    end_time    TIME        DEFAULT NULL            COMMENT '止（应用层校验不与已有时段重叠）',
    sort_order  INT         DEFAULT 0               COMMENT '排序（网格列序）',
    enabled     TINYINT     NOT NULL DEFAULT 1      COMMENT '启用 1/0',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_time_slot_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='时间段';

-- 课程（每生一门语文课，挂在 Student 下）
CREATE TABLE IF NOT EXISTS course (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    student_id  BIGINT      NOT NULL                COMMENT '归属学生',
    subject     VARCHAR(20) NOT NULL DEFAULT '语文' COMMENT '科目（固定「语文」）',
    price       DECIMAL(10,2) DEFAULT NULL          COMMENT '价格（冗余自 student.price 快照）',
    remark      VARCHAR(500) DEFAULT NULL           COMMENT '备注',
    enabled     TINYINT     NOT NULL DEFAULT 1      COMMENT '启用 1/0（停用保留历史）',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程（每生一门语文）';

-- 排课/上课记录（核心聚合根）
CREATE TABLE IF NOT EXISTS lesson (
    id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    student_id      BIGINT      DEFAULT NULL            COMMENT '学生',
    course_id       BIGINT      DEFAULT NULL            COMMENT '课程',
    slot_id         BIGINT      DEFAULT NULL            COMMENT '时间段（网格列）',
    lesson_date     DATE        DEFAULT NULL            COMMENT '上课日期（仅当月可排）',
    status          VARCHAR(20) NOT NULL DEFAULT 'UNTAKEN' COMMENT '状态: UNTAKEN/NORMAL/ABSENT/MADEUP/CANCELLED',
    absent_by       VARCHAR(20) DEFAULT NULL            COMMENT '请假方: student/teacher（仅 ABSENT 时有效）',
    absent_reason   VARCHAR(255) DEFAULT NULL           COMMENT '顺延原因（ABSENT 时必填）',
    make_up_date    DATE        DEFAULT NULL            COMMENT '补课日期（可跨月，不占新格；仅 MADEUP 承载）',
    closed          TINYINT     NOT NULL DEFAULT 0      COMMENT '待补关闭 0 未关闭 / 1 已关闭（与 status 正交）',
    remark          VARCHAR(500) DEFAULT NULL           COMMENT '备注',
    create_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lesson_date_slot (lesson_date, slot_id),
    KEY idx_lesson_student (student_id),
    KEY idx_lesson_status (status),
    KEY idx_lesson_pending (status, closed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排课/上课记录（核心）';

-- 字典（年级 / 顺延原因预设）
CREATE TABLE IF NOT EXISTS dict (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    dict_type   VARCHAR(30) NOT NULL                COMMENT '字典类型 grade / absent_reason',
    dict_value  VARCHAR(100) NOT NULL               COMMENT '字典值（如「小一」「学生病假」）',
    sort_order  INT         DEFAULT 0               COMMENT '排序',
    enabled     TINYINT     NOT NULL DEFAULT 1      COMMENT '启用 1/0',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_dict_type (dict_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典';
