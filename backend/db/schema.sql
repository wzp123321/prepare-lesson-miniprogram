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
    remark          VARCHAR(500) DEFAULT NULL           COMMENT '排课备注',
    prep_remark     VARCHAR(500) DEFAULT NULL           COMMENT '备课备注（与排课备注分开）',
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

-- ============================================================
-- 字典预置数据：年级（grade）/ 顺延原因（absent_reason）
-- 幂等写法：同 dict_type + dict_value 已存在则跳过，脚本可重复执行
-- ============================================================
INSERT INTO dict (dict_type, dict_value, sort_order, enabled)
SELECT s.dict_type, s.dict_value, s.sort_order, 1
FROM (
              SELECT 'grade'         AS dict_type, '一年级'   AS dict_value, 1 AS sort_order
    UNION ALL SELECT 'grade',                      '二年级',                2
    UNION ALL SELECT 'grade',                      '三年级',                3
    UNION ALL SELECT 'grade',                      '四年级',                4
    UNION ALL SELECT 'grade',                      '五年级',                5
    UNION ALL SELECT 'grade',                      '六年级',                6
    UNION ALL SELECT 'absent_reason',              '学生病假',              1
    UNION ALL SELECT 'absent_reason',              '学生事假',              2
    UNION ALL SELECT 'absent_reason',              '老师请假',              3
    UNION ALL SELECT 'absent_reason',              '法定节假日',            4
) s
WHERE NOT EXISTS (
    SELECT 1 FROM dict d
    WHERE d.dict_type = s.dict_type AND d.dict_value = s.dict_value
);

-- ============================================================
-- 备课模块（一期）
--   备课方式：按「年级 + 知识点」准备一份试题，上课通过试题讲解知识点（以题带点）
--   链路：知识点 → 题库 → 试卷(可派生) → 课次(挑知识点 + 挑卷)
-- ============================================================

-- 知识点
CREATE TABLE IF NOT EXISTS knowledge_point (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(100) NOT NULL                COMMENT '知识点名称，如「修辞手法」',
    grade       VARCHAR(32)  DEFAULT NULL            COMMENT '适用年级（dict.grade），空=通用',
    category    VARCHAR(32)  DEFAULT NULL            COMMENT '大类：字词/句子/阅读/古诗文/写作/基础',
    parent_id   BIGINT       DEFAULT NULL            COMMENT '父知识点（可选，支持二级）',
    sort_order  INT          NOT NULL DEFAULT 0      COMMENT '排序',
    enabled     TINYINT      NOT NULL DEFAULT 1      COMMENT '启用 1/0',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_kp_grade (grade),
    KEY idx_kp_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='语文知识点';

-- 题目（独立题库，可被多份试卷引用）
CREATE TABLE IF NOT EXISTS question (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    grade       VARCHAR(32)  DEFAULT NULL            COMMENT '年级',
    kp_id       BIGINT       DEFAULT NULL            COMMENT '考查知识点',
    student_id  BIGINT       DEFAULT NULL            COMMENT '归属学生，NULL=通用题库（派生卷克隆出的题带学生）',
    qtype       VARCHAR(32)  DEFAULT NULL            COMMENT '题型：选择/填空/判断/阅读/古诗文/写作/其他',
    stem        TEXT         NOT NULL                COMMENT '题干',
    options     JSON         DEFAULT NULL            COMMENT '选择题选项 ["A. …","B. …"]',
    answer      VARCHAR(1000) DEFAULT NULL           COMMENT '答案',
    analysis    VARCHAR(2000) DEFAULT NULL           COMMENT '解析（讲题要点）',
    difficulty  TINYINT      NOT NULL DEFAULT 2      COMMENT '难度 1易/2中/3难',
    source      VARCHAR(200) DEFAULT NULL            COMMENT '来源备注，如「2024 期末卷」',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_q_kp (kp_id),
    KEY idx_q_grade (grade),
    KEY idx_q_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题库';

-- 试卷（通用卷 / 学生派生卷）
CREATE TABLE IF NOT EXISTS paper (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    title           VARCHAR(200) NOT NULL                COMMENT '卷名，如「五年级 修辞手法 专项」',
    grade           VARCHAR(32)  DEFAULT NULL            COMMENT '年级',
    paper_type      VARCHAR(20)  NOT NULL DEFAULT 'KP'   COMMENT 'KP 知识点专项卷 / LESSON 课时题单',
    student_id      BIGINT       DEFAULT NULL            COMMENT '归属学生，NULL=通用卷',
    parent_paper_id BIGINT       DEFAULT NULL            COMMENT '派生自哪份卷（clone 溯源）',
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT 草稿 / READY 可用',
    remark          VARCHAR(500) DEFAULT NULL            COMMENT '备注',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_paper_grade (grade),
    KEY idx_paper_student (student_id),
    KEY idx_paper_type (paper_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷';

-- 试卷-题目编排
-- edited 语义：0 = 本卷这道题未被编辑，查卷时读 question 表原题；
--             1 = 本卷这道题被编辑过，查卷时读 paper_question_override 表。
CREATE TABLE IF NOT EXISTS paper_question (
    id          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    paper_id    BIGINT NOT NULL                COMMENT '试卷',
    question_id BIGINT NOT NULL                COMMENT '来源题目（题库引用）',
    sort_order  INT    NOT NULL DEFAULT 0      COMMENT '题号顺序',
    edited      TINYINT NOT NULL DEFAULT 0     COMMENT '卷内是否编辑过 1/0（1 时内容见 paper_question_override）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_paper_q (paper_id, question_id),
    KEY idx_pq_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷-题目编排';

-- 试卷-题目内容覆盖（卷内编辑）
-- 只有「被编辑过」的编排项在这里才有行；未编辑的题不占行，查卷时回落 question 表。
-- 这样：改某份卷的题面不影响题库原题，也不影响其他引用同一题的试卷。
CREATE TABLE IF NOT EXISTS paper_question_override (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    paper_question_id BIGINT       NOT NULL                COMMENT '所属编排项 paper_question.id',
    qtype            VARCHAR(32)   DEFAULT NULL            COMMENT '题型',
    stem             TEXT          NOT NULL                COMMENT '题干',
    options          JSON          DEFAULT NULL            COMMENT '选项 JSON 数组',
    answer           VARCHAR(1000) DEFAULT NULL            COMMENT '答案',
    analysis         VARCHAR(2000) DEFAULT NULL            COMMENT '解析',
    difficulty       TINYINT       DEFAULT NULL            COMMENT '难度 1易/2中/3难',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pqo_link (paper_question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷-题目内容覆盖（卷内编辑）';

-- 课次-知识点（一课多点）
CREATE TABLE IF NOT EXISTS lesson_knowledge (
    id        BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    lesson_id BIGINT NOT NULL                COMMENT '排课课次',
    kp_id     BIGINT NOT NULL                COMMENT '本节讲的知识点',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lesson_kp (lesson_id, kp_id),
    KEY idx_lk_kp (kp_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课次-知识点';

-- 课次-试卷（一课可多卷）
CREATE TABLE IF NOT EXISTS lesson_paper (
    id        BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    lesson_id BIGINT NOT NULL                COMMENT '排课课次',
    paper_id  BIGINT NOT NULL                COMMENT '本节使用的试卷',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lesson_paper (lesson_id, paper_id),
    KEY idx_lp_paper (paper_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课次-试卷';

-- ============================================================
-- 知识点预置数据（小学语文，按年级分组；可增删改停用）
-- 幂等写法：同 (grade, name) 已存在则跳过，脚本可重复执行
-- ============================================================
INSERT INTO knowledge_point (name, grade, category, sort_order, enabled)
SELECT s.name, s.grade, s.category, s.sort_order, 1
FROM (
              SELECT '一年级' AS grade, '字音（声母韵母）'       AS name, '字词' AS category,  1 AS sort_order
    UNION ALL SELECT '一年级',        '字形（笔画笔顺）',             '字词',           2
    UNION ALL SELECT '一年级',        '量词',                         '字词',           3
    UNION ALL SELECT '一年级',        '词语搭配',                     '字词',           4
    UNION ALL SELECT '一年级',        '句子仿写',                     '句子',           5
    UNION ALL SELECT '一年级',        '看图写话',                     '写作',           6
    UNION ALL SELECT '二年级',        '多音字',                       '字词',           1
    UNION ALL SELECT '二年级',        '形近字',                       '字词',           2
    UNION ALL SELECT '二年级',        '近义反义词',                   '字词',           3
    UNION ALL SELECT '二年级',        '标点符号基础',                 '句子',           4
    UNION ALL SELECT '二年级',        '把字句被字句',                 '句子',           5
    UNION ALL SELECT '二年级',        '看图写话',                     '写作',           6
    UNION ALL SELECT '三年级',        '成语积累',                     '字词',           1
    UNION ALL SELECT '三年级',        '关联词',                       '句子',           2
    UNION ALL SELECT '三年级',        '修改病句（成分残缺）',         '句子',           3
    UNION ALL SELECT '三年级',        '修辞（比喻拟人）',             '句子',           4
    UNION ALL SELECT '三年级',        '概括段意',                     '阅读',           5
    UNION ALL SELECT '三年级',        '记叙文阅读',                   '阅读',           6
    UNION ALL SELECT '三年级',        '习作（写人记事）',             '写作',           7
    UNION ALL SELECT '四年级',        '修辞（排比夸张）',             '句子',           1
    UNION ALL SELECT '四年级',        '修改病句（搭配不当）',         '句子',           2
    UNION ALL SELECT '四年级',        '句式变换',                     '句子',           3
    UNION ALL SELECT '四年级',        '说明方法（举例子列数字）',     '阅读',           4
    UNION ALL SELECT '四年级',        '概括主要内容',                 '阅读',           5
    UNION ALL SELECT '四年级',        '体会思想感情',                 '阅读',           6
    UNION ALL SELECT '四年级',        '习作（写景状物）',             '写作',           7
    UNION ALL SELECT '五年级',        '修改病句（语序不当）',         '句子',           1
    UNION ALL SELECT '五年级',        '说明方法（打比方作比较）',     '阅读',           2
    UNION ALL SELECT '五年级',        '人物形象分析',                 '阅读',           3
    UNION ALL SELECT '五年级',        '环境描写作用',                 '阅读',           4
    UNION ALL SELECT '五年级',        '古诗鉴赏',                     '古诗文',         5
    UNION ALL SELECT '五年级',        '文言实词',                     '古诗文',         6
    UNION ALL SELECT '五年级',        '习作（审题立意）',             '写作',           7
    UNION ALL SELECT '六年级',        '记叙顺序',                     '阅读',           1
    UNION ALL SELECT '六年级',        '标题作用',                     '阅读',           2
    UNION ALL SELECT '六年级',        '过渡照应',                     '阅读',           3
    UNION ALL SELECT '六年级',        '文言翻译',                     '古诗文',         4
    UNION ALL SELECT '六年级',        '古诗词默写',                   '古诗文',         5
    UNION ALL SELECT '六年级',        '名著阅读',                     '基础',           6
    UNION ALL SELECT '六年级',        '综合性学习',                   '基础',           7
    UNION ALL SELECT '六年级',        '习作（谋篇布局）',             '写作',           8
) s
WHERE NOT EXISTS (
    SELECT 1 FROM knowledge_point k
    WHERE k.grade = s.grade AND k.name = s.name
);

-- ============================================================
-- 结构迁移（幂等）：给已存在的旧库补列，可重复执行
-- ============================================================

-- lesson.prep_remark：备课备注从 lesson.remark 拆出（原先备课与排课共用 remark 一列）
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson' AND COLUMN_NAME = 'prep_remark'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE lesson ADD COLUMN prep_remark VARCHAR(500) DEFAULT NULL COMMENT ''备课备注（与排课备注分开）'' AFTER remark',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- paper_question 卷内编辑：早期版本曾在编排表上摊开 6 列 snap_* 快照。
-- 现改为「编排表只留 edited 标记 + 内容进 paper_question_override 表」，
-- 这里把旧库里遗留的 snap_* 列删掉（有才删，没有跳过）。
SET @tbl := 'paper_question';
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @tbl AND COLUMN_NAME = 'snap_qtype');
SET @ddl := IF(@c = 1, 'ALTER TABLE paper_question DROP COLUMN snap_qtype', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @tbl AND COLUMN_NAME = 'snap_stem');
SET @ddl := IF(@c = 1, 'ALTER TABLE paper_question DROP COLUMN snap_stem', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @tbl AND COLUMN_NAME = 'snap_options');
SET @ddl := IF(@c = 1, 'ALTER TABLE paper_question DROP COLUMN snap_options', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @tbl AND COLUMN_NAME = 'snap_answer');
SET @ddl := IF(@c = 1, 'ALTER TABLE paper_question DROP COLUMN snap_answer', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @tbl AND COLUMN_NAME = 'snap_analysis');
SET @ddl := IF(@c = 1, 'ALTER TABLE paper_question DROP COLUMN snap_analysis', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @tbl AND COLUMN_NAME = 'snap_difficulty');
SET @ddl := IF(@c = 1, 'ALTER TABLE paper_question DROP COLUMN snap_difficulty', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- 旧库若还没有 edited 列（更早版本），补上
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @tbl AND COLUMN_NAME = 'edited');
SET @ddl := IF(@c = 0, 'ALTER TABLE paper_question ADD COLUMN edited TINYINT NOT NULL DEFAULT 0 COMMENT ''卷内是否编辑过 1/0'' AFTER sort_order', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
