# 备课排课管理系统 — 前端概设（Overview-FE）

| 项目 | 内容 |
|---|---|
| 文档版本 | V1.0.0 |
| 迭代 | I1 |
| 阶段 | SDD 阶段一（概设，只产文档、不写代码） |
| 对应后端概设 | `archive-spec/product/overview-be-V1.0.0_I1_备课排课.md`（**已定稿 V1.0.0，本文件第 2 章已逐条对齐**） |
| 输入产物 | PRD（第 4 章功能、第 6 章状态机）、AGENTS.md（第 3 章前端规范）、CLAUDE.md、问题账本 `.sdd/questions.md` |
| 约束基线 | 组件库一律 Element Plus `el-*`；排课网格/拖拽在 Element Plus 之上自绘；不写 `src/` 生产代码 |
| 状态 | 待用户确认 |

> ✅ **接口契约说明**：后端概设已定稿（V1.0.0），本章第 2 章接口清单已与后端概设 **§2.4 接口契约（BE-API-01~33）逐条对齐并消项**，URL/方法/场景(S-xx)/关键字段要点以后端为唯一真相源。前端清单为后端 §2.4 的镜像，未自行新增字段或改 URL。

---

## 1. 设计目标与输入

### 1.1 设计目标
1. 把 PRD 的 10 个业务支线（`S01`~`S10`）拆解为**前端页面/路由**与 **`apis/` 接口模块**两层规划。
2. 锚定后端契约：`S-xx` 编号与后端一致，前端接口按 `S-xx` 归类，网关前缀统一为 `/api`。
3. 明确**组件库边界**：一律 `el-*`（Element Plus），排课网格与拖拽自绘，区域级视觉/交互下沉到 `prototype/` 的 UI 原型（不在本概设展开）。
4. 界定产出范围：本文件是唯一产出，**不写** `src/` 下任何生产代码，不写组件级布局/视觉。

### 1.2 输入与硬约束（来自问题账本 Q14 / AGENTS.md 第 3 章 / CLAUDE.md 第 5 章）
- **前端技术栈**：Vue 3 + Vite + TypeScript（`strict` 开启）。
- **组件库**：Element Plus（`el-table` / `el-dialog` / `el-form` / `el-select` / `el-date-picker` / `el-button` / `el-tag` / `el-card` 等）。**禁止** `te-*` / `@tiansu/*`。
- **高度定制交互**：排课网格、单元格拖拽建课在 Element Plus 之上自绘（原生 DOM + 少量封装），不强行套组件。
- **出图**：教师端「按学生出 PNG」走前端 `html2canvas` 截取网格（PRD 第 8 章）。
- **通信**：REST API（JSON）；前端 `vite` 代理或直连 `47.116.35.76`。
- **状态机**（PRD 第 6 章，全前端状态底色/标签直接映射）：
  `UNTAKEN`(未上) / `NORMAL`(正常上课) / `ABSENT`(顺延) / `MADEUP`(已补) / `CANCELLED`(作废)。

### 1.3 S-xx 业务支线与后端编号对照
| 编号 | 支线 | 前端职责 |
|---|---|---|
| S01 | 学生管理 | 列表/搜索/CRUD + 删除保护 |
| S02 | 时间段管理 | 列表/排序/CRUD + 重叠校验提示 |
| S03 | 课程管理 | 每生一门语文课配置 |
| S04 | 排课系统 | 当月网格拖拽建课、保存关闭上月顺延、按学生出 PNG |
| S05 | 请假顺延 | 标记顺延（必填请假方+原因）、状态切换 |
| S06 | 补课闭环与待补课 | 待补列表、手动关闭、标记已补 |
| S07 | 字典管理 | 年级 / 顺延原因预设维护 |
| S08 | 数据总览 | 按月卡片、历史明细、月收入、顺延分类 |
| S09 | 今日视图 | 落地页：今日课程 + 一键标记 + 待补提示 |
| S10 | 当月课程表 | 前台复用网格管状态、历史月只读 |

---

## 2. 接口契约（已与后端概设 §2.4 对齐 ✓）

> 对齐基准 = 后端概设 §2.4 接口契约（BE-API-01~33）；字段语义见后端 §2.3 数据字典。统一返回体 `{code, message, data}`；分页 `{list, total, page, size}`；网关前缀 `/api`，前端经 vite 代理或直连 `47.116.35.76`。本清单为后端 §2.4 的**镜像**，URL/方法/场景与后端完全一致，未自行新增字段或改 URL。

### S01 学生管理 `student`（已对齐 ✓ | BE-API-01~05）
| 编码 | 方法 | 路径 | 说明 | 关键入参/出参 | 域规则 |
|---|---|---|---|---|---|
| BE-API-01 | GET | `/api/students` | 列表+搜索 | `?keyword=&grade=&status=&page=&size=` → `{list:[Student], total}` | — |
| BE-API-02 | GET | `/api/students/{id}` | 详情 | → `Student & {pendingMakeUpCount}` | 含该生待补课数 |
| BE-API-03 | POST | `/api/students` | 新增 | body:`name,grade,phone,parentWechat,address,price,color,remark` | `color` 可自动分配 |
| BE-API-04 | PUT | `/api/students/{id}` | 修改 | body: 同上（部分字段） | — |
| BE-API-05 | DELETE | `/api/students/{id}` | 删除 | — | **删除保护**：存在未结课程或未来排课时返回 `409` + 提示先「暂停」归档 |

Student 字段（镜像后端 §2.3 `student`）：`id,name,grade,phone,parentWechat,address,price,status(1在读/0暂停),color,remark`（+ 公共 `createTime,updateTime`）。

### S02 时间段管理 `timeSlot`（已对齐 ✓ | BE-API-06~10）
| 编码 | 方法 | 路径 | 说明 | 关键入参/出参 | 域规则 |
|---|---|---|---|---|---|
| BE-API-06 | GET | `/api/time-slots` | 列表（按 sortOrder） | → `[TimeSlot]` | — |
| BE-API-07 | POST | `/api/time-slots` | 新增 | body:`name,startTime,endTime,sortOrder,enabled` | **重叠校验**：与已有时段交叉返回 `409` |
| BE-API-08 | PUT | `/api/time-slots/{id}` | 修改 | 同上 | 重叠校验 |
| BE-API-09 | DELETE | `/api/time-slots/{id}` | 删除 | — | 有排课引用返回 `409` + 引用计数，前端弹确认 |
| BE-API-10 | PUT | `/api/time-slots/sort` | 排序调整 | body:`[{id, sortOrder}]` | 批量更新排序 |

TimeSlot 字段（镜像后端 §2.3 `time_slot`）：`id,startTime,endTime,sortOrder,enabled`（前端草案中的 `name` 后端未定义 → **建议，待确认**是否补充该字段，当前以 `startTime~endTime` 展示）。

### S03 课程管理 `course`（已对齐 ✓ | BE-API-11~14）
| 编码 | 方法 | 路径 | 说明 | 关键入参/出参 | 域规则 |
|---|---|---|---|---|---|
| BE-API-11 | GET | `/api/students/{studentId}/course` | 查该生语文课 | → `Course\|null` | — |
| BE-API-12 | POST | `/api/students/{studentId}/course` | 建（每生一门） | body:`remark`；`price` 自动取自 `student.price` | `student_id` 已存在则 `409` |
| BE-API-13 | PUT | `/api/courses/{id}` | 修改 | body:`remark` | — |
| BE-API-14 | PUT | `/api/courses/{id}/disable` | 停用 | — | `enabled=0`，保留历史 |

Course 字段（镜像后端 §2.3 `course`）：`id,studentId,subject('语文'),price,remark,enabled`。

### S04 排课系统 `lesson`（已对齐 ✓ | BE-API-15~20）
| 编码 | 方法 | 路径 | 说明 | 关键入参/出参 | 域规则 |
|---|---|---|---|---|---|
| BE-API-15 | GET | `/api/lessons/month` | 当月网格数据 | `?year=&month=` → `{days:[], slots:[], cells:{[date#slotId]:LessonCell}}` | 仅当月 |
| BE-API-16 | POST | `/api/lessons` | 拖拽建课 | body:`studentId,slotId,lessonDate` | **每格 1 人**：同 `(lessonDate,slotId)` 冲突返回 `409`；`lessonDate` 须属当月 |
| BE-API-17 | DELETE | `/api/lessons/{id}` | 删课（拖出/点删） | — | — |
| BE-API-18 | PUT | `/api/lessons/{id}` | 改课（换时段/日期） | body:`slotId,lessonDate` | 同格冲突 `409` |
| BE-API-19 | POST | `/api/lessons/save-month` | **保存当月** | body: 当月网格变更集 → `{closedCount}` | ★核心域规则（见下） |
| BE-API-20 | GET | `/api/lessons/student/{studentId}/export` | 按学生出图数据 | `?year=&month=` → 该生当月课表 JSON | PNG 导出在前端 `html2canvas` 完成 |

**BE-API-19 保存排课关闭上月顺延（事务边界，S04/S06）**：① 应用提交当月网格变更（`lesson_date` 须属当前月）；② 查询上月 `status='ABSENT' AND closed=0` 记录批量置 `closed=1`（视为已安排进本月）；③ 返回 `closedCount`；④ 整组同一事务，校验仅限当月、同 `(lesson_date,slot_id)` 唯一冲突 `409`。

Lesson 字段（镜像后端 §2.3 `lesson`）：`id,studentId,courseId,slotId,lessonDate,status,absentBy,absentReason,makeUpDate,closed,remark`。

### S05 请假顺延 `lesson`（已对齐 ✓ | BE-API-21~22）
| 编码 | 方法 | 路径 | 说明 | 关键入参/出参 | 域规则 |
|---|---|---|---|---|---|
| BE-API-21 | PUT | `/api/lessons/{id}/absent` | 标记顺延 | body:`absentBy('student'\|'teacher'), absentReason` | **必填** `absentBy`+`absentReason`，缺失返回 `400`；置 `status=ABSENT, closed=0` 入待补 |
| BE-API-22 | PUT | `/api/lessons/{id}/status` | 状态切换 | body:`status('NORMAL'\|'MADEUP'\|'CANCELLED')` | `MADEUP` 经 BE-API-24 专用；此处 `CANCELLED` 留痕 |

### S06 补课闭环与待补课 `makeUp`（已对齐 ✓ | BE-API-23~25）
| 编码 | 方法 | 路径 | 说明 | 关键入参/出参 | 域规则 |
|---|---|---|---|---|---|
| BE-API-23 | GET | `/api/make-up/pending` | 待补列表 | → `[LessonCell]`（`status=ABSENT AND closed=0`，全局实时） | **无则不返回**（空数组，前端无则隐） |
| BE-API-24 | PUT | `/api/lessons/{id}/make-up` | 安排补课 | body:`makeUpDate`（可跨月） | 置 `status=MADEUP, closed=1`，写 `make_up_date` |
| BE-API-25 | PUT | `/api/make-up/{lessonId}/close` | 手动关闭（「已安排进本月课程」） | — | 仅置 `closed=1`（status 保持 ABSENT） |

### S07 字典管理 `dict`（已对齐 ✓ | BE-API-26~29）
| 编码 | 方法 | 路径 | 说明 | 关键入参/出参 | 域规则 |
|---|---|---|---|---|---|
| BE-API-26 | GET | `/api/dicts` | 列表（按 type） | `?type=grade\|absent_reason` → `[Dict]` | — |
| BE-API-27 | POST | `/api/dicts` | 新增 | body:`dictType,dictValue,sortOrder,enabled` | — |
| BE-API-28 | PUT | `/api/dicts/{id}` | 修改 | 同上 | — |
| BE-API-29 | DELETE | `/api/dicts/{id}` | 删除 | — | — |

Dict 字段（镜像后端 §2.3 `dict`）：`id,dictType,dictValue,sortOrder,enabled`。

### S08 数据总览 `statistics`（已对齐 ✓ | BE-API-30~32）
| 编码 | 方法 | 路径 | 说明 | 关键入参/出参 | 域规则 |
|---|---|---|---|---|---|
| BE-API-30 | GET | `/api/statistics/month` | 总览卡片 | `?year=&month=` → `{scheduled,normal,absent,absentByStudent,absentByTeacher,madeUp,cancelled,pending}` | N=排课数；X=normal；Y=absent(a+b)；Z=madeUp；W=cancelled；K=待补(ABSENT&closed=0) |
| BE-API-31 | GET | `/api/statistics/month/detail` | 历史未上课明细 | `?year=&month=` → `{list:[{lessonDate,studentName,slot,status,absentBy,absentReason}]}` | 列示 ABSENT 未补 + CANCELLED |
| BE-API-32 | GET | `/api/statistics/month/income` | 月收入 | `?year=&month=` → `{income, formula:'(NORMAL+MADEUP)×price'}` | 口径 Q2：`Σ`(NORMAL+MADEUP 课时的 `course.price`) |

### S09 今日视图 `lesson`（已对齐 ✓ | BE-API-33）
| 编码 | 方法 | 路径 | 说明 | 关键入参/出参 | 域规则 |
|---|---|---|---|---|---|
| BE-API-33 | GET | `/api/lessons/today` | 今日课程+待补提示 | → `{lessons:[LessonCell], pendingToday:[LessonCell]}` | `pendingToday` = 待补课中 `make_up_date = 今天` 者 |

### S10 当月课程表 `lesson`（已对齐 ✓ | 复用 BE-API-15/21/22）
- 读：复用 **BE-API-15** `GET /api/lessons/month`。
- 写：复用 **BE-API-21/22**（`/absent`、`/status`）。
- 约束：历史月（`lesson_date` 不在当前月）只读归档，后端对历史月写操作返回 `409`（或前端禁用）。

---

## 3. 前端支线

### 3.1 页面与路由
左侧导航分两组：**「管理」（后台录入）** 与 **「看板」（前台查看）**。落地页 = 看板「今日视图」。

| 分组 | 路由 | 页面 | 归属 S-xx | 主要 Element Plus 组件 |
|---|---|---|---|---|
| 管理 | `/admin/students` | 学生管理 | S01 | `el-table` `el-dialog` `el-form` `el-input` `el-select`(年级) `el-color-picker` `el-button` `el-tag`(状态) |
| 管理 | `/admin/time-slots` | 时间段管理 | S02 | `el-table` `el-dialog` `el-time-picker` `el-switch`(启用) `el-button`(排序) |
| 管理 | `/admin/courses` | 课程管理 | S03 | `el-table`(学生→语文课) `el-dialog` `el-input` `el-switch`(停用) |
| 管理 | `/admin/dicts` | 字典管理 | S07 | `el-tabs`(年级/顺延原因) `el-table` `el-dialog` `el-button` |
| 管理 | `/admin/schedule` | 排课系统 | S04 | 自绘月网格 + `el-dialog`(顺延/导出) `el-button` `el-select`(按学生出图) |
| 看板 | `/dashboard/today` | 今日视图（落地页） | S09 | `el-card` `el-table` `el-tag`(状态) `el-button`(一键标记) `el-alert`(待补提示) |
| 看板 | `/dashboard/overview` | 数据总览 | S08 | `el-card`(总览卡) `el-date-picker`(选月) `el-table`(未上课明细) `el-tag`(顺延分类) |
| 看板 | `/dashboard/monthly` | 当月课程表 | S10 | 复用 S04 网格（只读态+状态操作） |
| 看板 | `/dashboard/make-up` | 待补课首页 | S06 | `el-table`(待补) `el-button`(已安排进本月课程) `el-empty`(无则隐) |

> 注：S06 待补清单同时在 `/dashboard/today` 落地页以提示条出现；`/dashboard/make-up` 为独立全量页。

### 3.2 API 模块（`apis/` 层规划，按 S-xx 归类）
目录：`frontend/src/api/`（本概设仅规划，不写实现）。

| 模块文件 | 归属 S-xx | 封装接口（BE-API 编码，与后端 §2.4 一致） |
|---|---|---|
| `apis/student.ts` | S01 | BE-API-01~05 学生 CRUD + 详情（待补数） |
| `apis/timeSlot.ts` | S02 | BE-API-06~10 时间段 CRUD + 排序 |
| `apis/course.ts` | S03 | BE-API-11~14 每生语文课 CRUD/停用 |
| `apis/dict.ts` | S07 | BE-API-26~29 字典 CRUD（grade/absent_reason） |
| `apis/lesson.ts` | S04/S05/S09/S10 | BE-API-15~20 当月网格/建删改课/保存当月/出图；BE-API-21/22 顺延/状态；BE-API-33 今日视图 |
| `apis/makeUp.ts` | S06 | BE-API-23~25 待补列表、手动关闭、标记已补 |
| `apis/statistics.ts` | S08 | BE-API-30~32 月总览、历史明细、月收入 |

跨页共享状态（`store/`）：当前选中月份、待补课实时计数、字典缓存（年级/顺延原因）。

---

## 4. 前后端对齐检查（对齐基准 = 后端概设 §2.4）
| 项 | 前端起草 | 后端概设（§2.4） | 状态 |
|---|---|---|---|
| S-xx 编号体系 | S01~S10 已定义 | BE-API-01~33 已定义（§1.3 锚定 PRD 4.1~4.10） | ✅ 已对齐 ✓ |
| 实体字段 | 依据 PRD 5.1~5.5 | §2.3 数据字典（student/time_slot/course/lesson/dict） | ✅ 已对齐 ✓ |
| 统一返回体 | `{code,message,data}`（引 AGENTS 4.2） | §2.4 统一返回体一致 | ✅ 已对齐 ✓ |
| 排课保存关闭上月顺延 | `POST /api/lessons/save-month` ★域规则 | BE-API-19 事务边界与 `{closedCount}` 一致 | ✅ 已对齐 ✓ |
| 月收入口径 | `(NORMAL+MADEUP)×price`（Q2 已闭环） | BE-API-32 口径一致（Σ(NORMAL+MADEUP)×course.price） | ✅ 已对齐 ✓ |
| 每格仅 1 人冲突 | 前端实时拦截 + 后端 `409` | BE-API-16/18 同 `(lessonDate,slotId)` 冲突 `409` | ✅ 已对齐 ✓ |
| 时间段重叠 | 前端提示 + 后端 `409` | BE-API-07/08 重叠校验 `409` | ✅ 已对齐 ✓ |
| 删除保护（学生/时段） | 前端拦截 + 后端 `409` | BE-API-05/09 返回 `409` + 引用计数 | ✅ 已对齐 ✓ |
| 网关前缀/环境 | `/api` + vite 代理 `47.116.35.76` | §2.4 网关前缀 `/api` 一致（Q1 部署 `47.116.35.76`） | ✅ 已对齐 ✓ |
| 接口总数 | 33 条（S01~S10） | BE-API-01~33 共 33 条 | ✅ 已对齐 ✓（无新增/无遗漏） |

---

## 5. 自检清单
- [x] 章节结构完整：1 设计目标与输入 / 2 接口契约 / 3 前端支线 / 4 前后端对齐检查 / 5 自检清单
- [x] S-xx 编号与后端一致（S01~S10），接口草稿全部锚定 S-xx
- [x] 页面/路由覆盖：管理 5 页 + 看板 4 页，左导航分「管理」「看板」，落地页=今日视图
- [x] API 模块按场景规划：`student/timeSlot/course/dict/lesson/makeUp/statistics`
- [x] 组件库约束落地：全文 Element Plus `el-*`，排课网格/拖拽标注为自绘
- [x] 未写 `src/` 生产代码、未写组件级布局/视觉（归 `prototype/` UI 原型）
- [x] 未修改 PRD/AGENTS/CLAUDE/questions.md
- [x] 后端概设已定稿（V1.0.0）→ 第 2 章接口清单已逐条对齐 §2.4（BE-API-01~33）并消项，无「待与后端概设对齐」残留
- [x] 第 4 章前后端对齐检查全部「已对齐 ✓」，对齐基准=后端概设 §2.4
- [ ] 待确认项：S02 前端草案中 `time_slot.name` 后端 §2.3 未定义，已标注「建议，待确认」（不阻塞对齐）
- [ ] 待办：用户确认本概设后，进入 `prototype/` UI 原型与阶段二详设
