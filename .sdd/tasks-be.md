# 后端实现任务清单（SDD Stage 3 — 只列任务，不写实现）

> 本文档由「后端任务拆解者」基于后端概设 `archive-spec/product/overview-be-V1.0.0_I1_备课排课.md`、规范 `AGENTS.md` 第 4 章、问题账本 `.sdd/questions.md` 拆解。
> **硬约束（来自 questions.md / AGENTS）**：Spring Boot + MySQL 8.0 + MyBatis-Plus；独立服务，**禁止**引入天溯 `energy-parent` 私有依赖；统一返回体 `{code,message,data}`；DDD 分层（controller→application/service→domain→repository/mapper）；核心域规则写在 domain/service，不散落 controller；建表/DDL 不在此阶段写（本文件只列任务，实现留待详设/编码阶段）。
> **粒度约定**：建表 DDL、Entity 类、Mapper 接口+XML 各为独立任务；每个 REST 接口（Service 方法 + Controller 入口绑定）为独立任务（一个接口即一个最小可验证改动单元）。
> **待确认**：Q17（MySQL 库名/账号/密码）仅占位，不阻塞。

---

## Wave A — 基础设施（数据层与连接）
*目标：打通数据库连接、落 5 张表、建实体与 Mapper，为所有业务波次提供持久化底座。*

| 任务ID | 模块/场景 | 任务描述 | 可验证标准 | 依赖 |
|---|---|---|---|---|
| A-01 | 全局 | `application.yml` 配置 MySQL 连接占位（`47.116.35.76`），账号/密码/库名走环境变量占位，不硬编码 | 应用可启动、能连库；Q17 占位清晰，缺值时有明确报错提示 | 无 |
| A-02 | 全局/S-全部 | 建表 DDL：`student`（含 id/name/grade/phone/parent_wechat/address/price/status/color/remark/create_time/update_time） | 表创建成功；字段类型符合 PRD 5.1 / 概设 2.3 | A-01 |
| A-03 | 全局/S02 | 建表 DDL：`time_slot`（id/start_time/end_time/sort_order/enabled + 公共字段） | 表创建成功；`(start_time,end_time)` 配合应用层重叠校验 | A-01 |
| A-04 | 全局/S03 | 建表 DDL：`course`（id/student_id 唯一约束/subject/price/remark/enabled + 公共字段） | 表创建成功；`student_id` 唯一索引生效 | A-01 |
| A-05 | 全局/S04~S10 | 建表 DDL：`lesson`（id/student_id/course_id/slot_id/lesson_date/status/absent_by/absent_reason/make_up_date/closed/remark + 公共字段） | 表创建成功；`(lesson_date, slot_id)` 唯一约束 + `status`/`closed` 取值符合状态机 | A-01 |
| A-06 | 全局/S07 | 建表 DDL：`dict`（id/dict_type/dict_value/sort_order/enabled + 公共字段） | 表创建成功；`dict_type ∈ {grade,absent_reason}` | A-01 |
| A-07 | 全局 | Entity：`Student`（对应 A-02 字段，含 `pendingMakeUpCount` 非持久化扩展字段） | 编译通过；字段映射正确 | A-02 |
| A-08 | 全局/S02 | Entity：`TimeSlot` | 编译通过；映射 `time_slot` | A-03 |
| A-09 | 全局/S03 | Entity：`Course` | 编译通过；映射 `course` | A-04 |
| A-10 | 全局/S04~S10 | Entity：`Lesson`（含状态机枚举/常量 `UNTAKEN/NORMAL/ABSENT/MADEUP/CANCELLED`、`closed` 语义） | 编译通过；状态机值可约束 | A-05 |
| A-11 | 全局/S07 | Entity：`Dict` | 编译通过；映射 `dict` | A-06 |
| A-12 | 全局/S01 | Mapper：`StudentMapper` 接口 + XML（基础 CRUD + 按 keyword/grade/status 分页查询） | 单测/集成测试：增删改查与分页可用 | A-07 |
| A-13 | 全局/S02 | Mapper：`TimeSlotMapper` 接口 + XML（基础 CRUD + 按 sortOrder 列表 + 重叠区间查询辅助方法） | 列表按 `sort_order` 返回；重叠查询方法可用 | A-08 |
| A-14 | 全局/S03 | Mapper：`CourseMapper` 接口 + XML（基础 CRUD + 按 `student_id` 查唯一 + 唯一性校验） | `student_id` 唯一查询可用 | A-09 |
| A-15 | 全局/S04~S10 | Mapper：`LessonMapper` 接口 + XML（基础 CRUD + **上月 ABSENT&closed=0 批量查询**、**同 (lesson_date,slot_id) 冲突查询**、**按 student_id+year+month 查询**、**待补列表查询**、**引用计数查询**） | 上述自定义查询各自可单测 | A-10 |
| A-16 | 全局/S07 | Mapper：`DictMapper` 接口 + XML（基础 CRUD + 按 `dict_type` 列表） | 按类型查询可用 | A-11 |

---

## Wave B — 基础 CRUD（S01/S02/S03/S07）
*目标：学生、时间段、课程、字典的增删改查与各自业务规则。每个接口 = Service 方法 + Controller 入口。*

| 任务ID | 模块/场景 | 任务描述 | 可验证标准 | 依赖 |
|---|---|---|---|---|
| B-01 | S01 | BE-API-01 `GET /api/students` 列表+搜索（keyword/grade/status/page/size）→ `{list,total}` | 按条件返回分页；空条件返回全部 | A-12 |
| B-02 | S01 | BE-API-02 `GET /api/students/{id}` 详情 + `pendingMakeUpCount`（联 `lesson` 计 ABSENT&closed=0） | 返回详情与待补课数；无则 0 | A-12,A-15 |
| B-03 | S01 | BE-API-03 `POST /api/students` 新增（含 `color` 自动分配，可手动改） | 创建成功；无 color 时自动分配 hex | A-12 |
| B-04 | S01 | BE-API-04 `PUT /api/students/{id}` 修改（部分字段） | 指定字段更新成功 | A-12 |
| B-05 | S01 | BE-API-05 `DELETE /api/students/{id}` 删除保护（存在未结课程或未来排课时返回 `409` + 提示先「暂停」） | 有引用返回 409；无引用可删；`status=0` 归档不物理删 | A-12,A-15 |
| B-06 | S02 | BE-API-06 `GET /api/time-slots` 列表（按 `sortOrder`） | 按排序返回全部时段 | A-13 |
| B-07 | S02 | BE-API-07 `POST /api/time-slots` 新增（**重叠校验**：与已有时段 `[start,end)` 交叉返回 `409`） | 重叠返回 409；不重叠创建成功（依赖 C-06 域规则） | A-13,C-06 |
| B-08 | S02 | BE-API-08 `PUT /api/time-slots/{id}` 修改（重叠校验） | 同 B-07 校验（依赖 C-06） | A-13,C-06 |
| B-09 | S02 | BE-API-09 `DELETE /api/time-slots/{id}` 删除（有排课引用返回 `409` + 引用计数） | 有 `lesson` 引用返回 409 并带计数；无引用可删 | A-13,A-15 |
| B-10 | S02 | BE-API-10 `PUT /api/time-slots/sort` 排序调整（批量 `[{id,sortOrder}]`） | 批量更新排序成功 | A-13 |
| B-11 | S03 | BE-API-11 `GET /api/students/{studentId}/course` 查该生语文课 → `Course|null` | 返回该生课程或 null | A-14 |
| B-12 | S03 | BE-API-12 `POST /api/students/{studentId}/course` 建（每生一门，`price` 自动取自 `student.price`；`student_id` 已存在则 `409`） | 创建成功且 price 取自 student；重复返回 409 | A-14,A-12 |
| B-13 | S03 | BE-API-13 `PUT /api/courses/{id}` 修改（body: remark） | 备注更新成功 | A-14 |
| B-14 | S03 | BE-API-14 `PUT /api/courses/{id}/disable` 停用（`enabled=0`，保留历史） | 置 0 成功，历史数据仍在 | A-14 |
| B-15 | S07 | BE-API-26 `GET /api/dicts` 列表（按 `?type=grade|absent_reason`） | 按类型返回字典项 | A-16 |
| B-16 | S07 | BE-API-27 `POST /api/dicts` 新增（dictType/dictValue/sortOrder/enabled） | 新增成功 | A-16 |
| B-17 | S07 | BE-API-28 `PUT /api/dicts/{id}` 修改 | 修改成功 | A-16 |
| B-18 | S07 | BE-API-29 `DELETE /api/dicts/{id}` 删除 | 删除成功 | A-16 |

---

## Wave C — 排课核心（S04 / S02 删除保护 / S10 只读）
*目标：当月网格排课、每格 1 人、只排当月、保存关闭上月顺延（事务）、历史月只读。*

| 任务ID | 模块/场景 | 任务描述 | 可验证标准 | 依赖 |
|---|---|---|---|---|
| C-01 | S04 | 时间段重叠校验域规则（独立）：应用层判定 `[start_time,end_time)` 与已有时段交叉 | 单测覆盖交叉/边界/不交叉三种情形 | A-13 |
| C-02 | S04 | BE-API-15 `GET /api/lessons/month` 当月网格数据（`?year=&month=` → `{days,slots,cells}`） | 仅返回当月；结构含日期列/时段列/单元映射 | A-15,A-13,A-14 |
| C-03 | S04 | BE-API-16 `POST /api/lessons` 拖拽建课（body: studentId/slotId/lessonDate；**每格 1 人**冲突 `409`；`lessonDate` 须属当月） | 同 (date,slot) 冲突 409；非当月日期拒绝 | A-15,A-12,A-14,A-13 |
| C-04 | S04 | BE-API-17 `DELETE /api/lessons/{id}` 删课（拖出/点删） | 删除成功 | A-15 |
| C-05 | S04 | BE-API-18 `PUT /api/lessons/{id}` 改课（换时段/日期；同格冲突 `409`） | 换格冲突 409；合法修改成功 | A-15 |
| C-06 | S04 ★核心 | BE-API-19 `POST /api/lessons/save-month` 保存当月（事务边界）：提交当月网格变更集 + 批量关闭上月 `ABSENT&closed=0` + 返回 `closedCount`；校验 lessonDate 仅限当月、同格唯一 | 单事务；返回关闭条数；上月顺延被置 `closed=1`；冲突 409 | C-02,C-03,A-15 |
| C-07 | S10 | 历史月只读保护：对 `lesson_date` 不在当前月的写操作（BE-API-16/18 改、BE-API-21/22 状态）返回 `409`（或前端禁用，后端兜底拦截） | 历史月写请求被拒 | A-15 |

---

## Wave D — 顺延与补课（S05 / S06）
*目标：标记顺延（必填请假方+原因）、状态切换、待补查询（首页实时）、安排补课、手动关闭。*

| 任务ID | 模块/场景 | 任务描述 | 可验证标准 | 依赖 |
|---|---|---|---|---|
| D-01 | S05 | BE-API-21 `PUT /api/lessons/{id}/absent` 标记顺延（body: absentBy/ absentReason，**必填**，缺失 `400`；置 `status=ABSENT,closed=0` 入待补） | 缺参 400；成功置 ABSENT 且入待补；`absent_by ∈ {student,teacher}` | A-15,C-07 |
| D-02 | S05 | BE-API-22 `PUT /api/lessons/{id}/status` 状态切换（body: `NORMAL`/`CANCELLED`；`MADEUP` 走 D-04） | NORMAL 终态；CANCELLED 留痕终态 | A-15,C-07 |
| D-03 | S06 | BE-API-23 `GET /api/make-up/pending` 待补列表（全局实时 `ABSENT&closed=0`，**无则空数组**） | 返回待补数组；无则 `[]` | A-15 |
| D-04 | S06 | BE-API-24 `PUT /api/lessons/{id}/make-up` 安排补课（body: makeUpDate 可跨月；置 `status=MADEUP,closed=1`，写 `make_up_date`，**计入已补 Z**） | 写入补课日期并置 MADEUP+closed=1；跨月合法 | A-15 |
| D-05 | S06 | BE-API-25 `PUT /api/make-up/{lessonId}/close` 手动关闭（「已安排进本月课程」；仅置 `closed=1`，status 保持 ABSENT，**不计入已补 Z**） | 仅 closed=1；ABSENT 计数不变 | A-15 |

---

## Wave E — 统计（S08）
*目标：按月总览卡片、历史未上课明细、月收入（Q2 口径）。*

| 任务ID | 模块/场景 | 任务描述 | 可验证标准 | 依赖 |
|---|---|---|---|---|
| E-01 | S08 | BE-API-30 `GET /api/statistics/month` 总览卡片（`?year=&month=` → N/X/Y/Z/W/K + absentByStudent/absentByTeacher） | 计数正确；Y 拆 student/teacher 两类 | A-15,A-14 |
| E-02 | S08 | BE-API-31 `GET /api/statistics/month/detail` 历史未上课明细（列 ABSENT 未补 + CANCELLED） | 返回明细列表含 lessonDate/studentName/slot/status/absentBy/absentReason | A-15,A-14,A-12 |
| E-03 | S08 | BE-API-32 `GET /api/statistics/month/income` 月收入（`Σ(NORMAL+MADEUP 课时 × course.price)`，`formula` 透出） | 收入=Σ(NORMAL+MADEUP)×price；顺延未补/作废不计（Q2 口径） | A-15,A-14 |

---

## Wave F — 出图数据接口（S04，PNG 前端）
*目标：按学生当月课表数据输出，PNG 由前端 `html2canvas` 完成。*

| 任务ID | 模块/场景 | 任务描述 | 可验证标准 | 依赖 |
|---|---|---|---|---|
| F-01 | S04 | BE-API-20 `GET /api/lessons/student/{studentId}/export` 按学生出图数据（`?year=&month=` → 该生当月课表 JSON） | 返回该生当月课表结构；不含 PNG 生成逻辑 | A-15,A-14,A-13 |

---

## 关键跨波依赖汇总
1. **Wave A → 所有业务波**：DDL/Entity/Mapper 是所有 B/C/D/E/F 的前置。
2. **C-06（保存关闭上月顺延）** 是核心跨场景域规则，依赖 A-15 自定义查询（上月 ABSENT 批量查询）与 C-02/C-03 的网格读写。
3. **C-01（时间段重叠校验）** 被 B-07/B-08 复用（S02 新增/修改）。
4. **删除保护 / 引用计数**（B-05 学生、B-09 时段）依赖 A-15 的 `lesson` 引用计数查询。
5. **S03 课程创建**（B-12）依赖 S01 学生存在（A-12）。
6. **Wave C/D/E/F 的 lesson 读写** 均依赖 A-15 与 Wave B 的 student/course/time_slot 数据就绪。
7. **S10 历史月只读**（C-07）为 S04/S05 写接口的兜底护栏，需在 D-01/D-02 之前就绪。

## 待确认项
- **Q17**：MySQL 库名/账号/密码未定，A-01 仅占位，不阻塞任何波次。
- **PRD 10.2「已安排进本月课程」精确动作**：当前按概设定义为「仅关闭待补项」（对应 D-05），实际补课由教师另拖网格；若需按钮直生成补课单元需再确认（不影响本任务清单结构）。
- **PRD 10.3 跨多月待补**：更早月份遗留待补走首页手动关闭（D-05），是否足够待验证。
