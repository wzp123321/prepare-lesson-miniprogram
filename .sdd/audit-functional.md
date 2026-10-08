# 功能完整性审计（活体 · 逐文件读源码）

- 审计日期：2026-10-08
- 方法：逐文件通读 `backend/.../service/impl/*`、`*Controller`、`resources/mapper/*`、`frontend/src/views/*`、`frontend/src/api/*`、`frontend/src/router/*`、`frontend/src/utils/request.ts`，不信任任何既有评审结论。
- 结论速览：
  - **后端 33 接口：全部有真实 DB 逻辑，无空壳/占位/TODO。0 未实现/空壳。** 但有 1 处契约语义偏差（BE-API-24）和 1 个契约外端点。
  - **前端：4 个管理视图 + ScheduleView/TodayView/Overview 卡片与收入 均真实接 API 实现。**
  - **真正的功能缺口在「补课闭环的 MADEUP 完成态不可达」「Overview 历史明细表空」「Pinia store(W5-01) 缺失」三处。**

---

## A 后端（33 接口，全部 service/impl 已落地真实逻辑）

判定口径：空壳 = `return null`/空 list/TODO/写死常量；此处均未发现。

| BE-API | 功能 | 状态 | 证据 | 问题 |
|---|---|---|---|---|
| 01 | 学生列表+搜索 | 已实现 | StudentServiceImpl.java:41-57 | 分页+keyword/grade/status 真查库 |
| 02 | 学生详情(含 pendingCount) | 已实现 | StudentServiceImpl.java:59-71 | 联 lesson 计数 ABSENT&closed=0 |
| 03 | 学生新增 | 已实现 | StudentServiceImpl.java:73-91 | 校验+自动分配 color |
| 04 | 学生修改 | 已实现 | StudentServiceImpl.java:93-101,154-182 | 部分字段更新 |
| 05 | 学生删除(保护) | 已实现 | StudentServiceImpl.java:103-119 | 活跃课程/未来排课→409 |
| 06 | 时间段列表 | 已实现 | TimeSlotServiceImpl.java:22-25 | listOrdered |
| 07 | 时间段新增(重叠校验) | 已实现 | TimeSlotServiceImpl.java:27-37,95-107 | 重叠→409 |
| 08 | 时间段修改(重叠校验) | 已实现 | TimeSlotServiceImpl.java:39-49 | 同 |
| 09 | 时间段删除(引用计数) | 已实现 | TimeSlotServiceImpl.java:51-62 | 引用→409 带计数 |
| 10 | 时间段排序 | 已实现 | TimeSlotServiceImpl.java:64-78 | 批量更新 |
| 11 | 查该生课程 | 已实现 | CourseServiceImpl.java:19-22 | selectByStudentId |
| 12 | 建课程(每生一门) | 已实现 | CourseServiceImpl.java:24-42 | 重复→409，price 取自 student |
| 13 | 改课程备注 | 已实现 | CourseServiceImpl.java:44-52 | — |
| 14 | 停用课程 | 已实现 | CourseServiceImpl.java:54-63 | enabled=0 |
| 15 | 当月网格 | 已实现 | LessonServiceImpl.java:117-145 + LessonMapper.xml:72-90 | 真 join student 取姓名/色 |
| 16 | 拖拽建课(冲突409) | 已实现 | LessonServiceImpl.java:40-68 | 同格占用→409，仅当月 |
| 17 | 删课(历史月409) | 已实现 | LessonServiceImpl.java:70-76 | assertNotHistoryMonth |
| 18 | 改课(冲突409) | 已实现 | LessonServiceImpl.java:78-98 | 排除自身查冲突 |
| 19 | 保存当月关闭上月顺延 | 已实现 | LessonServiceImpl.java:100-115 + LessonMapper.xml:39-57 | 真查上月 ABSENT&closed=0 批量置 closed=1，返回 closedCount |
| 20 | 按学生出图数据 | 已实现 | StudentServiceImpl.java:131-151 + LessonMapper.xml:93-110 | 真数据，PNG 在前端 |
| 21 | 标记顺延 | 已实现 | LessonServiceImpl.java:149-169 | 必填 absentBy+reason→400，状态机 UNTAKEN→ABSENT |
| 22 | 状态切换 | 已实现 | LessonServiceImpl.java:171-189 | 状态机白名单+历史月护栏 |
| 23 | 待补列表 | 已实现 | LessonServiceImpl.java:246-250 + LessonMapper.xml:30-36 | 全局 ABSENT&closed=0 升序 |
| 24 | 安排补课 | **部分实现(契约偏差)** | LessonController.java:153-158；LessonServiceImpl.java:191-208 | **契约要求「写 make_up_date 且 status=MADEUP+closed=1」，实现拆为两步**：arrangeMakeUp 仅写 make_up_date(保持 ABSENT)，另增契约外端点 `made-up`(markMadeUp) 才置 MADEUP。见 D 节缺口 |
| 25 | 手动关闭待补 | 已实现 | LessonServiceImpl.java:230-244 + LessonController.java:173-177 | 仅 closed=1，status 保持 ABSENT |
| 30 | 月总览卡片 | 已实现 | StatisticsServiceImpl.java:28-72 | N/X/Y/Z/W/K + absentByStudent/Teacher 真分组计数 |
| 31 | 历史未上课明细 | 已实现(后端) | StatisticsServiceImpl.java:74-78 + StatisticsMapper.xml:20-35 | 后端返回裸数组（与前端形状不符，见 B 节） |
| 32 | 月收入 | 已实现 | StatisticsServiceImpl.java:80-90 + StatisticsMapper.xml:38-45 | 真 `SUM(c.price) WHERE status IN(NORMAL,MADEUP)`，Q2 口径正确 |
| 33 | 今日视图 | 已实现 | LessonServiceImpl.java:252-261 + LessonMapper.xml:139-170 | lessons + pendingToday(make_up_date=今天) |

> 补充：契约外端点 `PUT /lessons/{id}/made-up`(LessonController.java:163-167 / LessonServiceImpl.java:210-228) 是 MADEUP 的**唯一**达成路径，前端无任何调用入口（见 B 节）。

**后端未实现/空壳数：0**。**契约偏差：BE-API-24(1 处) + 额外端点(1 个)。**

---

## B 前端视图（仅为骨架/未接 API 才记空壳；此处多数真实）

| 视图 | 状态 | 证据 | 问题 |
|---|---|---|---|
| admin/StudentListView.vue | 已实现 | StudentListView.vue:5-12,77-88,142-161 | 列表/搜索/分页/增改/删除保护(409→alert)/暂停，全接真实 API |
| admin/TimeSlotListView.vue | 已实现 | TimeSlotListView.vue:5-11,53-62,89-100,162-191 | 增删改/排序(BE-API-10)/启用/重叠预校验+后端409/引用确认删除 |
| admin/CourseListView.vue | 已实现 | CourseListView.vue:5-6,41-53,61-94 | 按学生建课(409去重)/改备注/停用，接真实 API |
| admin/DictListView.vue | 已实现 | DictListView.vue:5,40-49,75-114 | grade/absent_reason 双 tab 增删改，接真实 API |
| admin/ScheduleView.vue | 已实现 | ScheduleView.vue:140-185(拖拽建/改课),279-295(html2canvas),235-259(保存关闭上月) | 拖拽+冲突拦截+出图+saveMonth 全部接真实 API |
| dashboard/TodayView.vue | 已实现 | TodayView.vue:8-9,47-73(标记),77-94(关闭待补) | 今日课程+顺延必填+待补关闭，接真实 API |
| dashboard/OverviewView.vue | **部分实现** | OverviewView.vue:48 `detail.value = det.list`；明细表列 :132-143 | 卡片/收入 OK；**历史未上课明细表恒空**（BE-API-31 后端返回裸数组，request 已拆 data，但视图取 `.list`→undefined）。且明细表缺 absentBy 列（契约 BE-API-31 含 absentBy） |
| dashboard/MonthlyView.vue | **部分实现** | MonthlyView.vue:295(已补 radio),120 `changeLessonStatus(id,{status:'MADEUP'})` | 网格+正常/顺延标记 OK；**「已补」选项被后端拒绝**：changeStatus 状态机仅允许 ABSENT→CANCELLED，MADEUP 只经契约外 `made-up` 端点(前端从未调用)→选「已补」必 400。无「安排补课日期」入口 |
| dashboard/MakeUpView.vue | **部分实现** | MakeUpView.vue:8(仅 import fetchPendingMakeUp,closeMakeUp),82 | 待补列表+手动关闭(BE-API-25)OK；**无「安排补课」(arrangeMakeUp) 入口、无「已补」入口** → 补课闭环中「写补课日期 / 已补完成」步骤在 UI 不可达 |
| store/(Pinia, W5-01) | **未实现** | `frontend/src/store/` 目录不存在 | 跨页状态用各视图局部 ref+api 直取，未用 Pinia。W5-01 显式未实现 |

**前端未实现/空壳数：1（store）。部分实现/缺陷：3（MakeUpView 补课闭环、MonthlyView 已补、OverviewView 明细表）。**

api 层核对：`student/timeSlot/course/dict/makeUp/statistics/lesson` 全部 `USE_MOCK=false` 直连 `/api`，无遗留 mock 兜底（仅 mockData.ts 保留冲突判断工具与类型）。`request.ts:28-29` 统一拆 `res.data`，故 BE-API-31 裸数组 → 视图 `.list` 为空。

---

## C PRD 五大功能覆盖

| PRD 功能 | 后端 | 前端 | 闭环判定 | 缺口 |
|---|---|---|---|---|
| ① 排课录入(S04) | BE-API-15~20 ✓ | ScheduleView 拖拽/保存/出图 ✓ | **闭环可用** | 无（依赖基础数据已存在，而学生/时段管理 UI 已具备） |
| ② 请假顺延(S05) | BE-API-21/22 ✓（必填校验+状态机） | TodayView/MonthlyView 标记 ✓ | **闭环可用** | 无 |
| ③ 补课闭环(S06) | BE-API-23/24/25 ✓，但 MADEUP 仅经契约外 `made-up` | MakeUpView 仅列表+手动关闭；无安排补课/已补入口 | **⚠ 不完整** | 待补可见+可手动关闭(保持 ABSENT)；「写补课日期(arrangeMakeUp)」「已补(MADEUP)」UI 不可达 → 已补 Z 与 MADEUP 收入恒为 0 |
| ④ 按月数据总览(S08) | BE-API-30/31/32 ✓ | OverviewView 卡片/收入 ✓；明细表 ✗ | **⚠ 部分闭环** | 历史未上课明细表因形状不符恒空（BE-API-31） |
| ⑤ 收入统计(S08/BE-API-32) | 公式正确 ✓ | OverviewView 展示 ✓ | **可见但缺维度** | 收入口径正确，但因 MADEUP 不可达，MADEUP 部分永远不计入 |

---

## D 未实现 / 空壳 / 缺陷清单（按优先级）

1. **【高】补课闭环 MADEUP 完成态不可达**（功能缺口，非空壳）
   - 根因：① 后端把契约 BE-API-24 的「安排补课」拆成 `arrangeMakeUp`(仅写日期,保持 ABSENT) + 契约外 `made-up`(markMadeUp 置 MADEUP+closed)；② 前端 `makeUp.ts:24` 的 `arrangeMakeUp` **从未被任何视图 import**（grep 确认仅定义）；③ 全仓无 `markMadeUp` 的 api 封装与调用；④ MonthlyView「已补」走 `changeLessonStatus` 被后端 `LessonServiceImpl.java:297-308` 状态机拒绝。
   - 影响：补课只能「手动关闭(BE-API-25,保持 ABSENT)」终结，无法登记「已补」完成态；`已补 Z` 统计、MADEUP 收入、今日该补(pendingToday,依赖 make_up_date) 全部失效。
   - 修复方向：在 MakeUpView 增加「安排补课日期」+「标记已补」两步按钮，调用 `arrangeMakeUp` 与新增的 `markMadeUp` api；或在后端把 BE-API-24 改回契约语义（一次置 MADEUP+closed+写日期）。

2. **【高】OverviewView 历史未上课明细表恒空**（集成 bug）
   - 证据：OverviewView.vue:48 `detail.value = det.list`；后端 StatisticsController.java:43-47 返回 `List<UnscheduledVO>`（裸数组），`request.ts:28-29` 已拆 `data`；而 `statistics.ts:33-35` 真实分支返回该裸数组，故 `det.list` 为 `undefined`。
   - 影响：BE-API-31 的历史顺延/作废明细在 UI 永不渲染。
   - 修复：后端改为返回 `{list:[...]}` 包装，或前端改为直接消费数组（去掉 `.list`）。另：明细表缺 absentBy 列（契约要求），建议补列。

3. **【中】Pinia store(W5-01) 未实现**
   - 证据：`frontend/src/store/` 目录不存在；所有视图以局部 ref + api 直取，无跨页状态共享。
   - 影响：功能可用，但不满足 W5-01 跨页状态约定（如当前月/选中学生未在 store 收敛）。可接受为已知偏差，需产品确认是否纳入范围。

---

## E 待确认项

1. **BE-API-24 契约偏差是否接受**：后端当前拆为 arrangeMakeUp + 契约外 `made-up` 两步。需产品/架构确认是「按契约改回一步」还是「前端补齐两步 UI」（见 D-1）。
2. **MADEUP 是否必须可经前端达成**：PRD 4.6 要求「已补」完成态；当前不可达。需确认是缺陷还是有意改为「仅手动关闭即视为闭环」。
3. **OverviewView BE-API-31 形状**：后端裸数组 vs 前端 `.list` 期望，需确认统一为 `{list}` 包装（推荐，符合其他接口分页/列表风格）还是前端去 `.list`。
4. **E-SafeNet 可读性**：本次读取的全部 `.java`/`.vue`/`.ts`/`.xml` 文件均无乱码/密文，均正常可读，无不可读文件。
5. **课程管理「启用」开关**：CourseListView 启用用 `updateCourse(remark)` 复用 BE-API-13（改备注）来实现启用，与契约 BE-API-13「修改备注」语义略有混用，建议确认是否需独立启用接口。
