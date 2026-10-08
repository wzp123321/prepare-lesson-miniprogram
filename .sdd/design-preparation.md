# 备课系统 详细设计（一期）

> **定位：不做"教案"，做"题库 + 组卷"。**
>
> 用户的真实备课方式（2026-10-08 澄清）：
> **按「年级 + 知识点」准备一份试题，上课时通过试题讲解知识点。**
> 不是写"教学目标/环节时间轴"那套教案，而是**以题带点**。

---

## 1. 需求澄清（本设计的事实基础）

| # | 问题 | 结论 | 对设计的影响 |
|---|---|---|---|
| C1 | 备课的产物是什么 | **一份试题**，按年级 + 知识点组织 | 核心实体是「试卷」+「题目」，不是「教案」 |
| C2 | 试卷是通用还是每生一份 | **通用 + 可改**：以通用卷为基础，给学生增删题目后另存 | 需要「派生（clone）」机制，且派生后互不影响 |
| C3 | 题目怎么进系统 | **逐题录入**：题干 / 题型 / 选项 / 答案 / 解析 分开填 | 题目是独立结构化数据，不是一坨文本 |
| C4 | 一节课与卷子的关系 | **一课多点**：一节课挑几个知识点讲，可能跨几份卷子取题 | 课**不绑定**单一卷子，而是绑「知识点 + 若干卷子」 |
| C5 | 同一道题会用在多份卷子吗 | **会，题目独立题库，可重复用** | 题目独立成表 + 卷题关联表 |
| C6 | 知识点清单从哪来 | **预设一版小学语文知识点，可增删** | 知识点独立成表，带年级 / 分类 / 启用 |

**已作废的旧设计**：`lesson_plan`（教案）+ `stages`（教学环节时间轴）+「一节课一份教案」——与 C1/C4 不符，全部移除。

---

## 2. 核心概念与闭环

```
知识点(knowledge_point)          ← 预设一版，可增删
      │
      ▼
题目(question)  独立题库，带年级+知识点+答案+解析
      │
      ▼  编排(paper_question)
试卷(paper)  通用卷 / 学生派生卷(从通用卷 clone，改后互不影响)
      │
      ▼  上课时挑选（课不绑死卷子）
课(lesson)  ── 讲哪些知识点(lesson_knowledge) ── 用哪些卷子(lesson_paper)
```

关键决策：

- **题目与试卷分离**（C5）：同一道题可编进多份卷子；题目改一次，引用它的所有通用卷同步变。
- **派生卷克隆题目**（C2）：给学生专门改卷时，连题目一起复制，改完**不影响**通用卷——符合"互不影响"的直觉。
- **一课多点**（C4）：课与知识点、课与卷子都是多对多，不设"一节课一份卷子"的限制。
- **一节 90 分钟的课** = 挑 2~3 个知识点 × 每个知识点讲几道题。

---

## 3. 数据模型

### 3.1 表清单

| 表 | 作用 | 分期 |
|---|---|---|
| `knowledge_point` | 知识点（预设一版小学语文，可增删） | 一期 |
| `question` | 题目（独立题库，可被多份卷子引用） | 一期 |
| `paper` | 试卷（通用卷 / 学生派生卷） | 一期 |
| `paper_question` | 卷 ↔ 题 编排（含题号顺序） | 一期 |
| `lesson_knowledge` | 课 ↔ 知识点（这节课讲什么） | 一期 |
| `lesson_paper` | 课 ↔ 试卷（这节课用哪些卷子） | 一期 |
| `material` / `plan_material` | 素材（课件、讲义） | 二期 |
| `student_mastery` | 学生知识点掌握度 | 三期 |
| `lesson_feedback` | 课后反馈 | 暂缓 |

### 3.2 DDL（MySQL 8，风格对齐 `backend/db/schema.sql`）

```sql
-- ===================== 知识点 =====================
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

-- ===================== 题目（独立题库，C3/C5） =====================
CREATE TABLE IF NOT EXISTS question (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    grade       VARCHAR(32)  DEFAULT NULL            COMMENT '年级',
    kp_id       BIGINT       DEFAULT NULL            COMMENT '考查知识点',
    student_id  BIGINT       DEFAULT NULL            COMMENT '归属学生，NULL=通用题库（派生卷克隆出的题带学生）',
    qtype       VARCHAR(32)  DEFAULT NULL            COMMENT '题型：选择/填空/判断/阅读/古诗文/写作/其他',
    stem        TEXT         NOT NULL                COMMENT '题干',
    options     JSON         DEFAULT NULL            COMMENT '选择题选项 ["A. …","B. …"]',
    answer      VARCHAR(1000) DEFAULT NULL           COMMENT '答案',
    analysis    VARCHAR(2000) DEFAULT NULL           COMMENT '解析（讲题要点，上课照着讲）',
    difficulty  TINYINT      NOT NULL DEFAULT 2      COMMENT '难度 1易 / 2中 / 3难',
    source      VARCHAR(200) DEFAULT NULL            COMMENT '来源备注，如「2024 期末卷」',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_q_kp (kp_id),
    KEY idx_q_grade (grade),
    KEY idx_q_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题库';

-- ===================== 试卷（C2） =====================
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

-- ===================== 卷-题编排（C5） =====================
CREATE TABLE IF NOT EXISTS paper_question (
    id          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    paper_id    BIGINT NOT NULL                COMMENT '试卷',
    question_id BIGINT NOT NULL                COMMENT '题目',
    sort_order  INT    NOT NULL DEFAULT 0      COMMENT '题号顺序',
    PRIMARY KEY (id),
    UNIQUE KEY uk_paper_q (paper_id, question_id),
    KEY idx_pq_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷-题目编排';

-- ===================== 课 ↔ 知识点 / 课 ↔ 试卷（C4） =====================
CREATE TABLE IF NOT EXISTS lesson_knowledge (
    id        BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    lesson_id BIGINT NOT NULL                COMMENT '排课课次',
    kp_id     BIGINT NOT NULL                COMMENT '本节讲的知识点',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lesson_kp (lesson_id, kp_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课次-知识点';

CREATE TABLE IF NOT EXISTS lesson_paper (
    id        BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    lesson_id BIGINT NOT NULL                COMMENT '排课课次',
    paper_id  BIGINT NOT NULL                COMMENT '本节使用的试卷',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lesson_paper (lesson_id, paper_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课次-试卷';

-- =====================【二期】素材 =====================
-- material(id,title,type,grade,kp_id,content,file_url,use_count,...)
-- 教材与课件，二期再做

-- =====================【暂缓】课后反馈 =====================
-- lesson_feedback(...)  用户明确暂缓（C7）
```

### 3.3 派生（clone）语义 —— C2 的关键实现

用户选「通用 + 可改」，所以：

```
POST /api/papers/clone { id, studentId }
  → 新 paper：student_id = 该生，parent_paper_id = 原卷，title 默认「原卷名（张三）」
  → 该卷所有 question **整行复制**（连同 options/answer/analysis），student_id = 该生
  → 新 paper_question 指向复制出来的新题
```

**为什么克隆题目而不是引用**：改学生卷的某道题时**不会污染**通用卷。代价是存储冗余，但题库量级（几百到几千题）完全无压力。

---

## 4. 预设知识点清单（C6）

一期随 `schema.sql` 幂等预置（`NOT EXISTS` 判重），按年级分组，用户可增删改停用：

| 年级 | 知识点 |
|---|---|
| 一年级 | 字音（声母韵母）、字形（笔画笔顺）、量词、词语搭配、句子仿写、看图写话 |
| 二年级 | 多音字、形近字、近义反义词、标点符号基础、把字句被字句、看图写话 |
| 三年级 | 成语积累、关联词、修改病句（成分残缺）、修辞（比喻拟人）、概括段意、记叙文阅读、习作（写人记事） |
| 四年级 | 修辞（排比夸张）、修改病句（搭配不当）、句式变换、说明方法（举例子/列数字）、概括主要内容、体会思想感情、习作（写景状物） |
| 五年级 | 修改病句（语序不当）、说明方法（打比方/作比较）、人物形象分析、环境描写作用、古诗鉴赏、文言实词、习作（审题立意） |
| 六年级 | 记叙顺序、标题作用、过渡照应、文言翻译、古诗词默写、名著阅读、综合性学习、习作（谋篇布局） |

`category` 统一归为：**字词 / 句子 / 阅读 / 古诗文 / 写作 / 基础**。

---

## 5. 接口清单

统一沿用现有约定：**POST + `/api/*` + `Result<T>`**。

### 5.1 知识点

| # | 接口 | 入参 | 返回 |
|---|---|---|---|
| BE-K-01 | `POST /api/kp/list` | `{grade?, category?, enabled?}` | `KnowledgePointVO[]` |
| BE-K-02 | `POST /api/kp/create` | `{name,grade,category,parentId,sortOrder,enabled}` | `Long` |
| BE-K-03 | `POST /api/kp/update` | `{id,...同上}` | `Void` |
| BE-K-04 | `POST /api/kp/delete` | `{id}` | `Void`（被题目/课引用时返回 409） |

### 5.2 题库

| # | 接口 | 入参 | 返回 | 说明 |
|---|---|---|---|---|
| BE-Q-01 | `POST /api/questions/list` | `{grade?,kpId?,qtype?,keyword?,studentId?,page,size}` | `Page<QuestionVO>` | 题库检索 |
| BE-Q-02 | `POST /api/questions/get` | `{id}` | `QuestionVO` | 详情 |
| BE-Q-03 | `POST /api/questions/create` | `{grade,kpId,qtype,stem,options,answer,analysis,difficulty,source}` | `Long` | 录题 |
| BE-Q-04 | `POST /api/questions/update` | `{id,...同上}` | `Void` | 改题（同步影响引用它的通用卷） |
| BE-Q-05 | `POST /api/questions/delete` | `{id}` | `Void` | 删题（被卷引用时返回 409） |

### 5.3 试卷

| # | 接口 | 入参 | 返回 | 说明 |
|---|---|---|---|---|
| BE-P-01 | `POST /api/papers/list` | `{grade?,studentId?,paperType?,keyword?}` | `PaperVO[]` | 卷列表（带题数） |
| BE-P-02 | `POST /api/papers/get` | `{id}` | `PaperDetailVO` | 卷详情（含题目列表，按题号） |
| BE-P-03 | `POST /api/papers/create` | `{title,grade,paperType,studentId,remark}` | `Long` | 建卷 |
| BE-P-04 | `POST /api/papers/update` | `{id,...同上}` | `Void` | 改卷 |
| BE-P-05 | `POST /api/papers/delete` | `{id}` | `Void` | 删卷（同时删编排，题目留库） |
| BE-P-06 | `POST /api/papers/clone` | `{id,studentId?}` | `Long` | **派生**：克隆卷 + 克隆题目（C2） |
| BE-P-07 | `POST /api/papers/questions/set` | `{paperId, questionIds:[...按题号顺序]}` | `Void` | 编排题目（整体覆盖） |
| BE-P-08 | `POST /api/papers/generate` | `{grade,kpIds[],countPerKp,difficulty?,title}` | `Long` | **一键组卷**：按知识点随机抽题成卷 |

### 5.4 备课（课 ↔ 知识点/卷）

| # | 接口 | 入参 | 返回 | 说明 |
|---|---|---|---|---|
| BE-L-01 | `POST /api/prep/todo` | `{from,to}` | `TodoPrepVO[]` | 待备课列表：区间课次 + 已配知识点/卷 + 备课状态 |
| BE-L-02 | `POST /api/prep/get` | `{lessonId}` | `LessonPrepVO` | 单节课备课详情 |
| BE-L-03 | `POST /api/prep/save` | `{lessonId, kpIds[], paperIds[], remark}` | `Void` | 保存某节课的备课安排（整体覆盖） |
| BE-L-04 | `POST /api/prep/suggest` | `{lessonId}` | `QuestionVO[]` | 按该生已选知识点，推荐可用题目（跨卷取题） |

---

## 6. 页面与交互

### P1 待备课（一级菜单「备课」→ 待备课）

按课次列出，一眼看到"这节课讲什么、卷子备好没"：

- 日期切换：今日 / 本周 / 自定义区间
- 每行：日期时间 · 学生 · **知识点标签** · 卷子数 · 状态（未备 / 已备 / 已上）
- 行内「去备课」→ 打开备课抽屉：勾选知识点（多选）+ 挑卷子（可多选）+ 备注
- 顶部统计：本周待备 N 节 / 已备 M 节

### P2 题库

- 筛选：年级 + 知识点 + 题型 + 关键词，分页列表
- 列表列：题干摘要、知识点、题型、难度、来源
- 编辑抽屉：题干、题型（选择/填空/…）、**选择题时动态出选项行**、答案、解析、难度、来源
- 支持"再录一题"连续录入（录题是高频动作，避免反复点新建）

### P3 试卷

- 列表：卷名、年级、类型（知识点卷 / 课时题单）、归属（通用 / 某学生）、题数、状态
- **一键组卷**：选年级 → 勾知识点 → 每点抽 N 题 → 生成草稿卷
- 卷详情：题号列表，可**从题库勾选加题**、上下移、删题、预览（按卷面样式打印/导出）
- **派生给学生**：选学生 → 克隆一份 → 之后增删改只影响这份
- 状态切换：草稿 → 可用

### P4 知识点管理

- 按年级 + 大类分组的清单，增 / 改 / 停用（停用不删除，历史题目仍可读）

---

## 7. 与现有系统的集成点

| 现有资产 | 复用方式 |
|---|---|
| `lesson` | 待备课的数据源；`lesson_id` 关联 |
| `student` | 卷子归属；按学生筛选专属卷 |
| `dict` | 年级（`grade`）、题型走字典 |
| 出图能力（html2canvas） | **卷子导出 PNG / 打印**，复用现有"按学生出图"那套 |
| `enabled` 约定 | 知识点沿用 `TINYINT 1/0`，前端 `el-switch` 必须 `:active-value="1" :inactive-value="0"` |
| 统一 Result / 异常 | 沿用 `BusinessException` + 409（外键被引用时拒绝删除） |

---

## 8. 分期计划

| 期 | 表 | 功能 | 解决什么 |
|---|---|---|---|
| **一期** | `knowledge_point` `question` `paper` `paper_question` `lesson_knowledge` `lesson_paper` | 知识点管理 + 题库 + 组卷/派生 + 待备课 | 卷子能攒、能复用、能针对学生改；上课前知道讲什么 |
| 二期 | `material` | 课件/讲义挂在知识点上 | 素材散落 |
| 三期 | `student_mastery` | 掌握度 → 自动推荐本节课讲哪些知识点 | 记不清学生薄弱点 |
| 暂缓 | `lesson_feedback` | 课后反馈 / 家长沟通 | 用户明确推迟 |

---

## 9. 技术选型与风险

| 项 | 决策 | 理由 |
|---|---|---|
| 题干 / 解析存储 | `TEXT` + `VARCHAR`，一期纯文本（支持换行、不解析富文本） | 语文题干含引号、下划线、分行，纯文本最稳；富文本二期再评估 |
| 选择题选项 | `JSON` 数组 `["A. …","B. …"]` | 题量小、只在题目内使用 |
| 派生卷 | 克隆题目而非引用（见 §3.3） | 保证"改学生卷不污染通用卷" |
| 删知识点 | 被引用时 409 拒绝 | 避免历史题目/课次出现悬空知识点 |
| 删题目 | 被卷引用时 409 拒绝 | 同上；想移除应先从卷里摘掉 |
| 风险 | 题量大后组卷随机可能重复抽到同题 | `generate` 按「未被本卷使用」过滤；数据量小时影响可忽略 |
| 风险 | 一课多点时"讲到第几题"无法记录 | 一期不记录进度；若需要，二期加 `lesson_question_status` |

---

## 10. 与竞品的对照（调研结论）

现成产品分四类，与本设计的对应关系：

| 类型 | 代表 | 借鉴 / 不借鉴 |
|---|---|---|
| 资源库型 | 国家中小学智慧教育平台、21世纪教育网、学科网 | ❌ 找现成教案，与"自建题库"路线不同，但印证了**试卷结构 = 目标/重难点/环节/分层练习** |
| AI 备课型 | 希沃AI备课、云知师AI、飞象老师（**上传旧教案自动翻新**） | ⚠️ 三期可加"AI 按知识点生题 / 翻新旧卷" |
| planbook 型 | Planbook、Common Planner | ✅ **复制上年计划**（可加）、模板、状态标签、日/周/月视图（我们已有） |
| AI 生成型 | MagicSchool（分字段输入 + 生成历史）、Eduaide（选中段落局部改写）、Diffit（一稿多难度） | ✅ 组卷参数表单化（已做）；⚠️ 局部改写 / 多难度版本可二期 |

用户路线与上述都不同：**核心是"题库 + 以题带点"**——国内资源站是"给你现成卷"，AI 工具是"给你现成内容"，我们要的是**"你自己的题攒起来 + 按知识点取用"**。

---

## 11. 决策记录

| # | 问题 | 结论 |
|---|---|---|
| C1 | 备课产物 | 按年级 + 知识点的一份**试题**（不是教案） |
| C2 | 试卷归属 | 通用 + 可改（派生克隆） |
| C3 | 题目录入 | 逐题录入（题干/题型/选项/答案/解析） |
| C4 | 课与卷关系 | 一课多点（课绑知识点 + 多份卷） |
| C5 | 题目复用 | 独立题库，可重复用（多卷共享同一题） |
| C6 | 知识点来源 | 预设一版小学语文（可增删） |
| C7 | 课后反馈 | 暂缓 |
| C8 | 备课菜单 | 独立一级菜单；整体菜单已改一级/二级可折叠 |
