# 备课排课管理系统 — 后端概设（Overview-BE）

| 项目 | 内容 |
|---|---|
| 文档版本 | V1.0.0 |
| 迭代 | I1 |
| 阶段 | SDD 阶段一（概设，只产文档、不写代码） |
| 对应前端概设 | `archive-spec/product/overview-fe-V1.0.0_I1_备课排课.md` |
| 输入产物 | PRD（第 4 章 4.1~4.10、第 5 章数据模型、第 6 章状态机）；`AGENTS.md`（第 4 章后端开发规范）；`CLAUDE.md`；问题账本 `.sdd/questions.md`（Q1~Q17 硬约束） |
| 方法论基线 | `backend/ai-knowledge/skill/` 下 `tiansu-ddd` / `tiansu-java-standards` / `design-document-skill` / `springboot-testing`（只借规范，不抄业务代码） |
| 技术栈 | Spring Boot（Maven）+ MySQL 8.0 + MyBatis-Plus；独立服务，不引天溯 `energy-parent` 私有依赖 |
| 范围边界 | 仅定契约：领域模型、状态机、数据字典（字段语义）、接口契约、权限矩阵、消息事件；**不写 DDL、不写 `src/` 任何生产代码** |
| 状态 | 待用户确认（确认后进入阶段二 `ts-design` 详设，DDL 与代码实现留待彼时） |

> ⚠️ 本文件是后端契约的唯一真相源。本文件未定义的决策（接口字段明细、DDL、类与包设计）均由后续详设/接口设计文档/数据库设计文档承接，且**不得引入本文件之外的决策**。前端概设第 2 章接口草稿已与本文件第 2.4 章逐条对齐并消项。

---

## 1 设计目标与输入

### 1.1 设计目标
1. 定稿**主线契约**：领域模型、状态机、数据字典、接口契约、权限矩阵、消息事件，作为阶段二详设与实现阶段的约束基线。
2. 定义并贯穿 **`S-xx` 业务场景编号**（S01~S10），每个锚定 PRD 第 4 章对应小节，使前后端、详设、测试用例共享同一套定位标识。
3. 明确 5 张核心表（`student` / `time_slot` / `course` / `lesson` / `dict`）的领域职责与字段语义（不含 DDL）。
4. 界定产出范围：本文件是唯一产出，**不写** `src/` 下任何生产代码、不写数据库表结构（DDL 留待 `ts-design`）。

### 1.2 输入与硬约束（源自 questions.md Q1~Q17 + AGENTS/CLAUDE）
- **数据库**：MySQL 8.0，部署于 `47.116.35.76`（Q1）；库名/账号/密码待补（Q17，不阻塞设计）。
- **ORM**：MyBatis-Plus（Q15 + AGENTS 4.3）；独立 Spring Boot，**禁止**引入天溯私有依赖体系。
- **月收入口径**：`(正常上课 NORMAL + 已补 MADEUP) × 该生课程价格`；顺延未补/作废不计（Q2 已闭环）。
- **补课跨月**：补课日期可落任意月，`make_up_date` 字段承载，不占新格（Q4）。
- **待补课关闭方式**：① 保存当月排课时自动关闭上月顺延；② 首页手动「已安排进本月课程」关闭（Q12）。
- **不做项**（PRD 1.3 / Q9~Q11）：假期停课日设置、JSON 备份导入导出、报表打印导出、登录与多角色（Q6）、移动端（Q5，Web 优先）。
- **状态机值**（PRD 第 6 章，硬约束）：`UNTAKEN`(未上) / `NORMAL`(正常上课) / `ABSENT`(顺延) / `MADEUP`(已补) / `CANCELLED`(作废)；`lesson.closed`(TINYINT) 标记待补是否已关闭；`absent_by` ∈ {student, teacher}；`make_up_date` 可跨月。

### 1.3 S-xx 业务场景编号（定义并贯穿全文）
| 编号 | 支线 | 锚定 PRD | 后端职责 |
|---|---|---|---|
| S01 | 学生管理 | 4.1 | 学生 CRUD + 搜索 + 删除保护 + 专属色/价格 |
| S02 | 时间段管理 | 4.2 | 时间段 CRUD + 排序 + 重叠校验 |
| S03 | 课程管理 | 4.3 | 每生一门语文课配置，价格联动 |
| S04 | 排课系统 | 4.4 | 当月网格拖拽建课、每格 1 人、只排当月、保存关闭上月顺延、按学生出图数据 |
| S05 | 请假顺延 | 4.5 | 标记顺延（必填 `absent_by`+`absent_reason`）与状态切换 |
| S06 | 补课闭环与待补课 | 4.6 | 待补定义/查询、安排补课写 `make_up_date`、关闭待补 |
| S07 | 字典管理 | 4.7 | 年级 / 顺延原因预设维护 |
| S08 | 数据总览 | 4.8 | 按月统计：排N/已上X/顺延Y/已补Z/作废W/待补K、月收入、顺延分类 |
| S09 | 今日视图 | 4.9 | 今日课程 + 一键标记 + 待补提示（落地页） |
| S10 | 当月课程表 | 4.10 | 前台复用网格管状态，历史月只读 |

---

## 2 主线契约

### 2.1 领域模型

五实体（聚合/实体边界依 `tiansu-ddd`）：

- **Student（聚合根，表 `student`）**：独立教师的学生主体，承载姓名、年级（字典值）、联系方式、家庭地址、课程单价 `price`、在读状态 `status`、专属着色 `color`、备注。是 `Course` 与 `Lesson` 的归属方。
- **Course（实体，表 `course`，挂在 Student 下）**：「某个学生的一门语文课」，每生唯一一条（`student_id` 唯一约束）。`price` 从 `student.price` 冗余快照，便于排课/统计时锁定历史单价；`enabled` 标示停用（保留历史）。
- **TimeSlot（实体，表 `time_slot`）**：可排课的时间段，由 `start_time`/`end_time`/`sort_order`/`enabled` 描述；作为排课网格的「列」。
- **Lesson（聚合根，核心，表 `lesson`）**：排课/上课记录，关联 `student_id` + `course_id` + `slot_id` + `lesson_date`。承载状态机（`status`）、顺延原因（`absent_by`/`absent_reason`）、补课日期（`make_up_date`）、待补关闭标志（`closed`）。是 S04~S06/S08~S10 的统一操作对象。
- **Dict（实体，表 `dict`）**：字典数据，由 `dict_type`（`grade` / `absent_reason`）+ `dict_value` 构成，供下拉与选项复用，独立无聚合依赖。

关系：`Student 1—1 Course`（每生一门）；`Student 1—N Lesson`；`Course 1—N Lesson`；`TimeSlot 1—N Lesson`；`Dict` 仅被 `Student.grade` 与顺延原因弹出框引用（值引用，非外键强约束）。

分层（依 AGENTS 4.2 DDD）：`controller`（REST 入口、参数校验、DTO）→ `application/service`（业务、事务、域规则）→ `domain`（实体、聚合、域规则如「保存排课关闭上月顺延」）→ `repository/mapper`（MyBatis-Plus 持久化）。**核心域规则写在 domain/service，禁止散落 controller**：① 排课保存时查询上月 `ABSENT` 课并批量关闭；② 同格（同 `lesson_date`+`slot_id`）仅 1 名学生冲突拦截；③ 时间段重叠校验；④ 月收入 = (NORMAL + MADEUP) × 学生价格。

### 2.2 状态机

状态字段 `lesson.status`（VARCHAR），取值与迁移：

```
         创建（拖入网格）
              │
              ▼
         [UNTAKEN] ──(标记正常上课)──▶ [NORMAL]
              │
              └──(标记顺延, 必填 absent_by + absent_reason)──▶ [ABSENT]
                                                            │
                                     ┌──────────────────────┼──────────────────────┐
                                     ▼                      ▼                      ▼
                              [MADEUP](安排补课完成)    [CANCELLED](作废,留痕)   待补显示中(closed=0)
                                     │
                                     ▼
                               closed = 1（待补关闭）
```

| 状态值 | 含义 | 进入条件 | 出向迁移 |
|---|---|---|---|
| `UNTAKEN` | 未上 | 新建排课（未来日期默认） | → `NORMAL`（到点/标记正常上课）；→ `ABSENT`（标记顺延） |
| `NORMAL` | 正常上课 | 标记正常上课 | 终态（历史归档） |
| `ABSENT` | 顺延 | 标记顺延（必填 `absent_by`+`absent_reason`） | → `MADEUP`（安排补课）；→ `CANCELLED`（作废） |
| `MADEUP` | 已补 | 安排补课（写 `make_up_date`） | 置 `closed=1`，终态 |
| `CANCELLED` | 作废 | 标记作废（不补、留痕） | 终态 |

**待补判定（贯穿 S04/S06/S09）**：`status = 'ABSENT' AND closed = 0` 的课即「待补课」。

**`closed` 标志语义（关键契约）**：
- 仅 `closed` 控制待补是否还在清单中，与 `status` 正交。
- **自动关闭（保存排课，S04/S06）**：仅置 `closed=1`，`status` 保持 `ABSENT`（视作「已安排进本月」消除待补显示，**不计入「已补 Z」**）。
- **手动关闭（首页「已安排进本月课程」，S06）**：同，仅置 `closed=1`。
- **安排补课（S06）**：置 `status=MADEUP` + `closed=1` + 写 `make_up_date`，**计入「已补 Z」**。

> 统计口径由此唯一确定（S08）：`已补 Z` 只数 `status=MADEUP` 的课时；自动/手动关闭仅消除待补显示，不改变上月顺延计数。

**`absent_by`**：`student`（学生请假）/ `teacher`（老师请假），仅 `ABSENT` 时有效，用于 S08 顺延分类计数。
**`make_up_date`**：DATE，可跨月，不占新格；仅 `MADEUP` 时承载；供 S09「今日该补的待补课」判断（`make_up_date = 今天`）。

### 2.3 数据字典（字段语义，不含 DDL）

> 仅描述领域与字段语义；建表语句/索引/类型精度由阶段二数据库设计文档承接。公共字段（依 `tiansu-java-standards`/设计文档规范）：各表统一含 `id`(BIGINT 主键)、`create_time`、`update_time`。

**`student`（学生）**
| 字段 | 语义 | 说明 |
|---|---|---|
| id | 主键 | BIGINT |
| name | 姓名 | 必填 |
| grade | 年级 | 取值自 `dict`(`dict_type=grade`)，如 小一~小六/初一~初三 |
| phone | 电话 | 选填 |
| parent_wechat | 家长微信 | 选填 |
| address | 家庭地址 | 必填（上门/路线参考） |
| price | 课程价格 | DECIMAL(10,2) 元/节，月收入统计用 |
| status | 在读状态 | TINYINT：1 在读 / 0 暂停（归档） |
| color | 专属颜色 | VARCHAR hex，课表着色 |
| remark | 备注 | 选填 |

**`time_slot`（时间段）**
| 字段 | 语义 | 说明 |
|---|---|---|
| id | 主键 | BIGINT |
| start_time | 起 | TIME |
| end_time | 止 | TIME（应用层校验不与已有时段重叠） |
| sort_order | 排序 | INT，网格列序 |
| enabled | 启用 | TINYINT |

**`course`（课程，每生一门语文）**
| 字段 | 语义 | 说明 |
|---|---|---|
| id | 主键 | BIGINT |
| student_id | 归属学生 | BIGINT FK，唯一（每生一门） |
| subject | 科目 | VARCHAR 固定「语文」 |
| price | 价格 | DECIMAL(10,2) 冗余自 `student.price` 快照 |
| remark | 备注 | 选填 |
| enabled | 启用 | TINYINT（停用保留历史） |

**`lesson`（排课/上课记录，核心）**
| 字段 | 语义 | 说明 |
|---|---|---|
| id | 主键 | BIGINT |
| student_id | 学生 | BIGINT FK |
| course_id | 课程 | BIGINT FK |
| slot_id | 时间段 | BIGINT FK（网格列） |
| lesson_date | 上课日期 | DATE，仅当月可排 |
| status | 状态 | VARCHAR，见 2.2 状态机 |
| absent_by | 请假方 | VARCHAR：`student`/`teacher`（ABSENT 时） |
| absent_reason | 顺延原因 | VARCHAR（ABSENT 时必填） |
| make_up_date | 补课日期 | DATE，可跨月，不占新格 |
| closed | 待补关闭 | TINYINT：0 未关闭 / 1 已关闭 |
| remark | 备注 | 选填 |

**`dict`（字典）**
| 字段 | 语义 | 说明 |
|---|---|---|
| id | 主键 | BIGINT |
| dict_type | 字典类型 | VARCHAR：`grade` / `absent_reason` |
| dict_value | 字典值 | VARCHAR（如「小一」「学生病假」） |
| sort_order | 排序 | INT |
| enabled | 启用 | TINYINT |

### 2.4 接口契约

> 统一返回体 `{code, message, data}`（依 AGENTS 4.2）；分页 `{list, total, page, size}`。网关前缀 `/api`，前端经 vite 代理或直连 `47.116.35.76`（Q1）。以下接口与前端概设第 2 章草稿**已逐条对齐**。接口编码 `BE-API-xx` 供全文引用；引用一律写编码，不写章节号。

#### 2.4.1 S01 学生管理
| 编码 | 方法 | 路径 | 说明 | 关键入参/出参 | 域规则 |
|---|---|---|---|---|---|
| BE-API-01 | GET | `/api/students` | 列表+搜索 | `?keyword=&grade=&status=&page=&size=` → `{list:[Student], total}` | — |
| BE-API-02 | GET | `/api/students/{id}` | 详情 | → `Student & {pendingMakeUpCount}` | 含该生待补课数 |
| BE-API-03 | POST | `/api/students` | 新增 | body:`name,grade,phone,parentWechat,address,price,color,remark` | `color` 可自动分配 |
| BE-API-04 | PUT | `/api/students/{id}` | 修改 | body: 同上（部分字段） | — |
| BE-API-05 | DELETE | `/api/students/{id}` | 删除 | — | **删除保护**：存在未结课程或未来排课时返回 `409` + 提示先「暂停」归档 |

#### 2.4.2 S02 时间段管理
| 编码 | 方法 | 路径 | 说明 | 关键入参/出参 | 域规则 |
|---|---|---|---|---|---|
| BE-API-06 | GET | `/api/time-slots` | 列表（按 sortOrder） | → `[TimeSlot]` | — |
| BE-API-07 | POST | `/api/time-slots` | 新增 | body:`name,startTime,endTime,sortOrder,enabled` | **重叠校验**：与已有时段交叉返回 `409` |
| BE-API-08 | PUT | `/api/time-slots/{id}` | 修改 | 同上 | 重叠校验 |
| BE-API-09 | DELETE | `/api/time-slots/{id}` | 删除 | — | 有排课引用返回 `409` + 引用计数，前端弹确认 |
| BE-API-10 | PUT | `/api/time-slots/sort` | 排序调整 | body:`[{id, sortOrder}]` | 批量更新排序 |

#### 2.4.3 S03 课程管理
| 编码 | 方法 | 路径 | 说明 | 关键入参/出参 | 域规则 |
|---|---|---|---|---|---|
| BE-API-11 | GET | `/api/students/{studentId}/course` | 查该生语文课 | → `Course\|null` | — |
| BE-API-12 | POST | `/api/students/{studentId}/course` | 建（每生一门） | body:`remark`；`price` 自动取自 `student.price` | `student_id` 已存在则 `409` |
| BE-API-13 | PUT | `/api/courses/{id}` | 修改 | body:`remark` | — |
| BE-API-14 | PUT | `/api/courses/{id}/disable` | 停用 | — | `enabled=0`，保留历史 |

#### 2.4.4 S04 排课系统（核心）
| 编码 | 方法 | 路径 | 说明 | 关键入参/出参 | 域规则 |
|---|---|---|---|---|---|
| BE-API-15 | GET | `/api/lessons/month` | 当月网格数据 | `?year=&month=` → `{days:[], slots:[], cells:{[date#slotId]:LessonCell}}` | 仅当月 |
| BE-API-16 | POST | `/api/lessons` | 拖拽建课 | body:`studentId,slotId,lessonDate` | **每格 1 人**：同 `(lessonDate,slotId)` 冲突返回 `409`；`lessonDate` 须属当月 |
| BE-API-17 | DELETE | `/api/lessons/{id}` | 删课（拖出/点删） | — | — |
| BE-API-18 | PUT | `/api/lessons/{id}` | 改课（换时段/日期） | body:`slotId,lessonDate` | 同格冲突 `409` |
| BE-API-19 | POST | `/api/lessons/save-month` | **保存当月** | body: `{year, month}`（仅关闭上月 ABSENT&closed=0，返回 `{closedCount}`） | ★核心域规则（见下） |
| BE-API-20 | GET | `/api/lessons/student/{studentId}/export` | 按学生出图数据 | `?year=&month=` → 该生当月课表 JSON | PNG 导出在前端 `html2canvas` 完成（Q4/PRD 8） |

**BE-API-19 保存排课关闭上月顺延（事务边界，S04/S06）**：
1. 应用已在 BE-API-16/17/18 逐条落库的当月排课；本接口仅负责关闭上月顺延，不再接收网格变更集。
2. 查询 `lesson` 中 `lesson_date` 属**上月**（由入参 `year`+`month` 减一月计算）且 `status='ABSENT' AND closed=0` 的记录，批量置 `closed=1`（视为已安排进本月）。
3. 返回 `closedCount`（关闭条数）。
4. 整组操作在同一事务。

#### 2.4.5 S05 请假顺延
| 编码 | 方法 | 路径 | 说明 | 关键入参/出参 | 域规则 |
|---|---|---|---|---|---|
| BE-API-21 | PUT | `/api/lessons/{id}/absent` | 标记顺延 | body:`absentBy('student'\|'teacher'), absentReason` | **必填** `absentBy`+`absentReason`，缺失返回 `400`；置 `status=ABSENT, closed=0` 入待补 |
| BE-API-22 | PUT | `/api/lessons/{id}/status` | 状态切换 | body:`status('NORMAL'\|'MADEUP'\|'CANCELLED')` | `MADEUP` 经 BE-API-24 专用；此处 `CANCELLED` 留痕 |

#### 2.4.6 S06 补课闭环与待补课
| 编码 | 方法 | 路径 | 说明 | 关键入参/出参 | 域规则 |
|---|---|---|---|---|---|
| BE-API-23 | GET | `/api/make-up/pending` | 待补列表 | → `[LessonCell]`（`status=ABSENT AND closed=0`，全局实时） | **无则不返回**（空数组，前端无则隐） |
| BE-API-24 | PUT | `/api/lessons/{id}/make-up` | 安排补课 | body:`makeUpDate`（可跨月） | 置 `status=MADEUP, closed=1`，写 `make_up_date` |
| BE-API-25 | PUT | `/api/make-up/{lessonId}/close` | 手动关闭（「已安排进本月课程」） | — | 仅置 `closed=1`（status 保持 ABSENT） |

#### 2.4.7 S07 字典管理
| 编码 | 方法 | 路径 | 说明 | 关键入参/出参 | 域规则 |
|---|---|---|---|---|---|
| BE-API-26 | GET | `/api/dicts` | 列表（按 type） | `?type=grade\|absent_reason` → `[Dict]` | — |
| BE-API-27 | POST | `/api/dicts` | 新增 | body:`dictType,dictValue,sortOrder,enabled` | — |
| BE-API-28 | PUT | `/api/dicts/{id}` | 修改 | 同上 | — |
| BE-API-29 | DELETE | `/api/dicts/{id}` | 删除 | — | — |

#### 2.4.8 S08 数据总览
| 编码 | 方法 | 路径 | 说明 | 关键入参/出参 | 域规则 |
|---|---|---|---|---|---|
| BE-API-30 | GET | `/api/statistics/month` | 总览卡片 | `?year=&month=` → `{scheduled,normal,absent,absentByStudent,absentByTeacher,madeUp,cancelled,pending}` | N=排课数；X=normal；Y=absent(a+b)；Z=madeUp；W=cancelled；K=待补(ABSENT&closed=0) |
| BE-API-31 | GET | `/api/statistics/month/detail` | 历史未上课明细 | `?year=&month=` → `{list:[{lessonDate,studentName,slot,status,absentBy,absentReason}]}` | 列示 ABSENT 未补 + CANCELLED |
| BE-API-32 | GET | `/api/statistics/month/income` | 月收入 | `?year=&month=` → `{income, formula:'(NORMAL+MADEUP)×price'}` | 口径 Q2：`Σ`(NORMAL+MADEUP 课时的 `course.price`) |

#### 2.4.9 S09 今日视图（落地页）
| 编码 | 方法 | 路径 | 说明 | 关键入参/出参 | 域规则 |
|---|---|---|---|---|---|
| BE-API-33 | GET | `/api/lessons/today` | 今日课程+待补提示 | → `{lessons:[LessonCell], pendingToday:[LessonCell]}` | `pendingToday` = 待补课中 `make_up_date = 今天` 者 |

#### 2.4.10 S10 当月课程表
- 读：复用 **BE-API-15** `GET /api/lessons/month`。
- 写：复用 **BE-API-21/22**（`/absent`、`/status`）。
- 约束：历史月（`lesson_date` 不在当前月）只读归档，后端对历史月写操作返回 `409`（或前端禁用）。

### 2.5 权限矩阵

PRD 第 2 章结论：**唯一角色「教师（管理员）」，单人使用，暂不做登录与多角色（Q6）**。

| 角色 | 来源 | 学生 | 时间段 | 课程 | 排课 | 状态/顺延 | 补课 | 字典 | 统计 | 今日 |
|---|---|---|---|---|---|---|---|---|---|---|
| 教师（管理员） | PRD 2 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

**结论**：MVP 不引入鉴权，所有接口对唯一教师开放；后端**不做**登录/会话/权限拦截。预留：若后续 Q6 演进为多角色，`lesson`/各资源加 `owner` 或引入 RBAC，本概设不展开。

### 2.6 消息事件

- **MVP 不引入消息队列 / 异步事件总线**：单机单用户，无对外事件集成需求。
- 可预留**领域事件**（仅扩展钩子，不实现）：`LessonAbsentEvent`（顺延产生，供未来待补提醒）、`MakeUpClosedEvent`（待补关闭）。当前阶段不落地，不定义生产者/消费者。
- 跨服务依赖：无（questions.md Q15，无对外 Feign 诉求）；外部依赖仅 MySQL（`47.116.35.76`，Q1/Q17）。

---

## 3 后端支线（按 S-xx）

> 每个支线列出：涉及实体/表、归属接口（编码）、关键域规则。与第 2 章契约一致。

### 3.1 S01 学生管理（锚定 PRD 4.1）
- 实体/表：`student`。
- 接口：BE-API-01~05。
- 关键域规则：删除保护（BE-API-05，有未结课程/未来排课禁硬删，须先改「暂停」）；`color` 自动分配可手动改；详情返回 `pendingMakeUpCount`（联 `lesson` 计 ABSENT&closed=0）。

### 3.2 S02 时间段管理（锚定 PRD 4.2）
- 实体/表：`time_slot`。
- 接口：BE-API-06~10。
- 关键域规则：新增/修改**重叠校验**（与已有时段 `[start_time,end_time)` 交叉即 `409`）；作为网格列（`sort_order`）；删除有引用时 `409` + 引用计数。

### 3.3 S03 课程管理（锚定 PRD 4.3）
- 实体/表：`course`（依赖 `student`）。
- 接口：BE-API-11~14。
- 关键域规则：每生唯一一条语文课（`student_id` 唯一）；`price` 冗余自 `student.price`；`disable` 仅置 `enabled=0` 保留历史。

### 3.4 S04 排课系统（锚定 PRD 4.4）
- 实体/表：`lesson`（依赖 `student`/`course`/`time_slot`）。
- 接口：BE-API-15~20。
- 关键域规则：① 拖拽建课每格 1 人（BE-API-16/18 冲突 `409`）；② 只排当月（`lesson_date` 属当前月校验）；③ **保存关闭上月顺延**（BE-API-19 事务，见 2.4.4）；④ 按学生出图仅提供数据，PNG 在前端。

### 3.5 S05 请假顺延（锚定 PRD 4.5）
- 实体/表：`lesson`。
- 接口：BE-API-21/22。
- 关键域规则：标记顺延**必填** `absent_by`+`absent_reason`（缺失 `400`），置 `ABSENT`+`closed=0` 入待补；状态切换 `NORMAL`/`CANCELLED` 经 BE-API-22。

### 3.6 S06 补课闭环与待补课（锚定 PRD 4.6）
- 实体/表：`lesson`。
- 接口：BE-API-23~25。
- 关键域规则：待补定义 `ABSENT AND closed=0`（BE-API-23 全局实时，无则空数组）；安排补课写 `make_up_date` 置 `MADEUP`+`closed=1`（BE-API-24，可跨月）；手动关闭仅 `closed=1`（BE-API-25）。

### 3.7 S07 字典管理（锚定 PRD 4.7）
- 实体/表：`dict`。
- 接口：BE-API-26~29。
- 关键域规则：`dict_type` ∈ {`grade`, `absent_reason`}；被 `student.grade` 与顺延弹出框引用。

### 3.8 S08 数据总览（锚定 PRD 4.8）
- 实体/表：`lesson`（联 `course` 取 `price`）、`student`。
- 接口：BE-API-30~32。
- 关键域规则：按月聚合——N/X/Y/Z/W/K 计数（Y 拆 `absentByStudent`/`absentByTeacher`）；月收入 `Σ(NORMAL+MADEUP 课时 × course.price)`（Q2）；历史月未上课明细列 ABSENT+CANCELLED。

### 3.9 S09 今日视图（锚定 PRD 4.9）
- 实体/表：`lesson`。
- 接口：BE-API-33（含 BE-API-21/22 一键标记复用）。
- 关键域规则：返回今日课程 + `pendingToday`（`make_up_date=今天` 的待补课提示）。

### 3.10 S10 当月课程表（锚定 PRD 4.10）
- 实体/表：`lesson`。
- 接口：复用 BE-API-15（读）、BE-API-21/22（写）。
- 关键域规则：复用 S04 网格；历史月只读归档（写操作 `409`）。

---

## 4 自检清单

- [x] 章节结构：1 设计目标与输入 / 2 主线契约（2.1~2.6）/ 3 后端支线（S01~S10）/ 4 自检清单 —— 完整无跳号。
- [x] `S-xx` 编号已定义并贯穿全文（1.3、2.4、3.x），且锚定 PRD 第 4 章对应小节（4.1~4.10）。
- [x] 5 张表已界定领域与字段语义（`student`/`time_slot`/`course`/`lesson`/`dict`），未写 DDL。
- [x] 状态机值齐备（`UNTAKEN`/`NORMAL`/`ABSENT`/`MADEUP`/`CANCELLED`），含 `closed`/`absent_by`/`make_up_date` 语义，已澄清「自动关闭不计入已补」口径。
- [x] 接口契约覆盖全部要求：学生/时间段/课程/字典 CRUD；排课保存（含关闭上月顺延）；标记顺延（必填 absent_by+absent_reason）；安排补课（写 make_up_date）；待补课查询（无则不返回）；数据总览（N/X/Y/Z/W/K + 月收入 + 顺延分类）；按学生出图（后端给数据、PNG 前端）；共 33 个接口 BE-API-01~33。
- [x] 接口 URL 与前端概设第 2 章草稿逐条对齐，前端「待与后端概设对齐」项已消项。
- [x] 权限矩阵结论：单角色教师=管理员，MVP 不鉴权，预留多角色演进。
- [x] 消息事件：MVP 不引 MQ，预留领域事件钩子（不实现）。
- [x] 未写 `src/` 任何生产代码、未写 DDL（建表/索引留待阶段二数据库设计文档）。
- [x] 未修改 PRD / AGENTS / CLAUDE / questions.md。
- [x] 硬约束注入：月收入口径(Q2)、补课跨月(Q4)、待补关闭方式(Q12)、5 表与状态机(questions.md 硬约束)均已落契约。
- [ ] 待确认（不阻塞设计）：Q17 MySQL 库名/账号/密码（由用户补充，application.yml 先占位）。
- [ ] 待办：用户确认本概设后，进入阶段二 `ts-design` 详设 + 接口设计文档 + 数据库设计文档（落地 DDL 与字段明细、类与包设计）。
