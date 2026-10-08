# 前端实现任务清单（tasks-fe）— 备课排课管理系统

> 角色：前端任务拆解者（SDD Stage 3，仅产任务文档、不写实现代码）
> 迭代：I1 ｜ 版本：V1.0.0
> 输入产物：
> - 前端概设 `archive-spec/product/overview-fe-V1.0.0_I1_备课排课.md`（§2 接口契约、§3 页面与路由/API 模块 S01~S10）
> - UI 原型交接件 `archive-spec/product/ui-V1.0.0_I1_备课排课/_notes.md`（区域→组件映射）
> - 原型参考 `prototype/S04_排课网格.vue`、`prototype/S09_今日视图.vue`（仅参考交互，已落盘不重写）
> - 规约 `AGENTS.md`（3.2 前端规范：Vue3+Vite+TS+Element Plus `el-*`，禁 `te-*`）
> - 问题账本 `.sdd/questions.md`（硬约束 `el-*`）
> - 后端概设 `archive-spec/product/overview-be-V1.0.0_I1_备课排课.md`（BE-API-01~33 为唯一真相源）
>
> 硬约束（全程不得违反）：
> 1. 组件库一律 `el-*`（Element Plus）；排课网格/拖拽在 Element Plus 之上自绘，**禁止** `te-*` / `@tiansu/*`。
> 2. 不写 `src/` 或 `frontend/` 下任何实现代码；本文件与 `checklist-fe.md` 为唯一产出。
> 3. 接口 URL/方法/字段语义以 BE-API-01~33 为准，前端仅按 S-xx 归类封装，不新增/不改 URL。
> 4. 状态机 `UNTAKEN/NORMAL/ABSENT/MADEUP/CANCELLED` 全局底色/标签一致；`closed` 控制待补显示，与 `status` 正交。

---

## 波次总览

| 波次 | 主题 | 任务数 | 覆盖 S-xx | 主要依赖（后端） |
|---|---|---|---|---|
| Wave 1 | 工程脚手架 | 7 | — | 无（纯前端） |
| Wave 2 | 管理页 | 10 | S01/S02/S03/S07 | 后端 Wave A：BE-API-01~14、26~29 |
| Wave 3 | 排课网格 | 6 | S04 | 后端 Wave B：BE-API-15~20 |
| Wave 4 | 前台看板 | 5 | S09/S08/S10/S06 + S05 标记 | 后端 Wave C/D/E：BE-API-21~25、30~33 |
| Wave 5 | 联调验证 | 5 | 全量 | 后端 Wave A~E 全部就绪且部署 `47.116.35.76` |

> 总任务数：**33**。

---

## Wave 1 — 工程脚手架（纯前端，不依赖后端）

### W1-01 工程初始化（Vue3 + Vite + TS，strict）
- 【波次 Wave】Wave 1 工程脚手架
- 【页面/场景 S-xx】全局脚手架
- 【任务描述】在 `frontend/` 下创建 Vue3 + Vite + TypeScript 工程：`package.json`、`vite.config.ts`、`tsconfig.json`（开启 `strict`）、`index.html`、`src/main.ts`、`src/App.vue`、`src/env.d.ts`；目录按 AGENTS 2.2 建立 `api/ components/ views/ router/ store/ utils/ styles/`。
- 【可验证标准】`npm install` 后 `npm run build` 通过；`vue-tsc` 类型检查可执行；`src/` 下无业务实现代码（仅空壳/占位）。
- 【依赖】无

### W1-02 Element Plus 接入（app.use）
- 【波次 Wave】Wave 1 工程脚手架
- 【页面/场景 S-xx】全局脚手架
- 【任务描述】`main.ts` 中 `app.use(ElementPlus)` 并 `import 'element-plus/dist/index.css'`；注册 `ElMessage`/`ElMessageBox` 全局可用；确认未引入任何 `te-*` 组件。
- 【可验证标准】在 `App.vue` 临时渲染一个 `el-button` 可正常显示并带 Element Plus 主题样式；构建无 `te-*` 引用报错。
- 【依赖】W1-01

### W1-03 axios 封装 + 统一返回体/错误处理
- 【波次 Wave】Wave 1 工程脚手架
- 【页面/场景 S-xx】全局 `utils/request.ts`
- 【任务描述】封装 `axios` 实例：baseURL=`/api`；响应拦截解析统一返回体 `{code,message,data}`；业务 `code≠成功` 时 `ElMessage.error(message)`；HTTP `400`→提示参数/必填缺失；`409`→统一冲突提示（不静默吞掉，由具体页决定后续动作）。错误中心化处理，避免每页重复。
- 【可验证标准】编写单测或 mock 验证：① 正常返回透传 `data`；② `400`/`409` 触发对应提示文案；③ 网络异常触发错误态。
- 【依赖】W1-01、W1-02

### W1-04 vite 代理配置（/api → 47.116.35.76）
- 【波次 Wave】Wave 1 工程脚手架
- 【页面/场景 S-xx】全局 `vite.config.ts`
- 【任务描述】配置 `server.proxy['/api']` 指向 `http://47.116.35.76`（IP 来自 Q1/后端概设 2.4）；注释说明生产构建由后端托管/独立部署时无需代理。
- 【可验证标准】`vite.config.ts` 含代理配置且有清晰注释；`npm run dev` 时请求 `/api/...` 被正确转发（后端就绪后可联调验证）。
- 【依赖】W1-01

### W1-05 vue-router 路由表（9 路由，落地页=today）
- 【波次 Wave】Wave 1 工程脚手架
- 【页面/场景 S-xx】`router/index.ts`
- 【任务描述】建立路由：管理组 `/admin/students`、`/admin/time-slots`、`/admin/courses`、`/admin/dicts`、`/admin/schedule`；看板组 `/dashboard/today`（落地页/默认重定向）、`/dashboard/overview`、`/dashboard/monthly`、`/dashboard/make-up`。路由与前端概设 §3.1 完全一致。
- 【可验证标准】路由表含全部 9 条；访问 `/` 或未知路径重定向到 `/dashboard/today`；各路径可匹配占位页面（无 404）。
- 【依赖】W1-01、W1-02

### W1-06 统一布局（左导航两组 + 内容区）
- 【波次 Wave】Wave 1 工程脚手架
- 【页面/场景 S-xx】`layouts/AdminLayout.vue` / `DashboardLayout.vue` 或统一 `AppLayout.vue`
- 【任务描述】自绘左侧导航（用 `el-menu` 或原生 + `el-*`，分「管理」「看板」两组），右侧 `<router-view/>` 主内容区；导航高亮当前路由；看板落地页为今日视图。组件库边界：用 `el-menu`/`el-container` 等 `el-*` 组合，不引入 `te-*`。
- 【可验证标准】布局渲染；点击左导航可在管理/看板 9 页间切换；当前路由项高亮。
- 【依赖】W1-05

### W1-07 全局类型定义 + apis 模块骨架
- 【波次 Wave】Wave 1 工程脚手架
- 【页面/场景 S-xx】`api/types.ts` + `api/*.ts` 骨架
- 【任务描述】定义实体 TS 类型：`Student`、`TimeSlot`、`Course`、`Lesson`（`LessonCell`）、`Dict`、`LessonStatus` 枚举（UNTAKEN/NORMAL/ABSENT/MADEUP/CANCELLED）、`AbsentBy`；建立 7 个空壳模块 `student.ts`、`timeSlot.ts`、`course.ts`、`dict.ts`、`lesson.ts`、`makeUp.ts`、`statistics.ts`，导出函数签名占位（参数/返回类型对齐 BE-API，实现留待各波次填充）。
- 【可验证标准】`vue-tsc` 类型检查通过（类型齐全、无 `any` 滥用）；7 个模块文件存在且导出签名与 BE-API 编码一一对应。
- 【依赖】W1-01、W1-03

---

## Wave 2 — 管理页（S01/S02/S03/S07）

> 后端依赖：Wave A（BE-API-01~14、26~29）。每个页面拆为「apis 封装 / 列表 / 表单(+域规则)」三个最小可验证任务。

### W2-01 S01 apis/student.ts 封装
- 【波次 Wave】Wave 2 管理页
- 【页面/场景 S-xx】S01 学生管理
- 【任务描述】实现 `student.ts`：封装 BE-API-01（列表+搜索 `?keyword=&grade=&status=&page=&size=`）、BE-API-02（详情含 `pendingMakeUpCount`）、BE-API-03（新增）、BE-API-04（修改）、BE-API-05（删除）。入参/出参类型对齐 W1-07。
- 【可验证标准】函数签名与 BE-API-01~05 一致；调用后返回结构符合 `{list,total}` 与 `Student`；类型检查通过。
- 【依赖】W1-07、W1-03

### W2-02 S01 学生列表 + 搜索页
- 【波次 Wave】Wave 2 管理页
- 【页面/场景 S-xx】S01 学生管理 `/admin/students`
- 【任务描述】用 `el-table` 展示学生列表；`el-input`(姓名/年级搜索) + `el-select`(年级，数据来自字典 S07) + `el-select`(状态 在读/暂停)；分页；「新增」「编辑」「删除」入口；状态用 `el-tag`。
- 【可验证标准】页面展示学生列表；按姓名/年级/状态筛选结果正确；分页可用；五态中 `empty` 无数据显示 `el-empty`。
- 【依赖】W2-01、W1-06

### W2-03 S01 学生表单 + 删除保护
- 【波次 Wave】Wave 2 管理页
- 【页面/场景 S-xx】S01 学生管理
- 【任务描述】`el-dialog` + `el-form` 新增/编辑：姓名(必填)、年级(`el-select` 字典)、电话、家长微信、地址(必填)、价格(`el-input-number`)、专属颜色(`el-color-picker`，可自动分配可改)、备注、状态(在读/暂停)。保存调用 W2-01 新增/修改。删除：调用 BE-API-05 遇 `409` 时 `ElMessageBox` 提示「存在未结课程或未来排课，请先改为暂停归档」，不硬删。
- 【可验证标准】新增/编辑后列表即时刷新；删除有未结记录时弹 `409` 提示且不删除；删除无冲突记录成功；颜色选择器生效并随课表着色。
- 【依赖】W2-01、W2-02

### W2-04 S02 apis/timeSlot.ts 封装
- 【波次 Wave】Wave 2 管理页
- 【页面/场景 S-xx】S02 时间段管理
- 【任务描述】实现 `timeSlot.ts`：BE-API-06（列表按 sortOrder）、BE-API-07（新增，含 `name,startTime,endTime,sortOrder,enabled`）、BE-API-08（修改）、BE-API-09（删除，含引用计数处理）、BE-API-10（排序调整 `PUT /sort`）。
- 【可验证标准】函数签名对齐 BE-API-06~10；排序批量提交结构 `[{id,sortOrder}]` 正确；类型检查通过。
- 【依赖】W1-07、W1-03

### W2-05 S02 时间段列表 + 排序
- 【波次 Wave】Wave 2 管理页
- 【页面/场景 S-xx】S02 时间段管理 `/admin/time-slots`
- 【任务描述】`el-table` 按 `sortOrder` 展示时段；`el-switch`(启用) 即时切换；提供排序调整（上/下移动或拖拽，提交 BE-API-10）；删除触发 BE-API-09，有排课引用时后端返 `409`+引用计数，前端 `ElMessageBox.confirm` 展示引用数并询问。
- 【可验证标准】列表按序展示；启用开关生效；排序保存后顺序持久；引用中删除弹确认并显示引用计数，确认后调用删除。
- 【依赖】W2-04、W1-06

### W2-06 S02 时间段表单 + 重叠校验提示
- 【波次 Wave】Wave 2 管理页
- 【页面/场景 S-xx】S02 时间段管理
- 【任务描述】`el-dialog`+`el-form` 新增/编辑：`el-time-picker`(起止)、排序、启用；保存提交 BE-API-07/08。遇后端重叠校验 `409`（与已有时段区间交叉）时 `ElMessage.warning` 提示冲突且不写入。
- 【可验证标准】正常新增/编辑成功；故意与已有时段交叉提交时收到 `409` 并提示「时间段重叠」，表单不关闭、记录不写入。
- 【依赖】W2-04、W2-05
- 【待确认】`time_slot.name` 后端 §2.3 未定义（前端概设 §2 S02 标注「建议，待确认」）。若后端不增字段，则表单仅以 `startTime~endTime` 展示、不采集 `name`；定版后再调整表单字段。

### W2-07 S03 apis/course.ts 封装
- 【波次 Wave】Wave 2 管理页
- 【页面/场景 S-xx】S03 课程管理
- 【任务描述】实现 `course.ts`：BE-API-11（查该生语文课 `GET /students/{id}/course`）、BE-API-12（建，每生一门，body 仅 `remark`，`price` 自动取自 student）、BE-API-13（修改 remark）、BE-API-14（停用 `disable`）。
- 【可验证标准】函数签名对齐 BE-API-11~14；建课重复 `student_id` 返 `409` 可被处理；类型检查通过。
- 【依赖】W1-07、W1-03

### W2-08 S03 课程管理页（每生一门语文课）
- 【波次 Wave】Wave 2 管理页
- 【页面/场景 S-xx】S03 课程管理 `/admin/courses`
- 【任务描述】`el-table` 以「学生→语文课」呈现：列示学生、科目(固定「语文」)、价格(联动 `student.price`)、备注、启用状态(`el-switch`)；未建课学生提供「建课」入口(BE-API-12)；已建课可改备注(BE-API-13)/停用(BE-API-14，保留历史)。
- 【可验证标准】每生仅一条语文课配置；建课后价格自动取自学生单价；停用后状态正确且不删除历史；重复建课 `409` 提示。
- 【依赖】W2-07、W1-06、W2-02

### W2-09 S07 apis/dict.ts 封装
- 【波次 Wave】Wave 2 管理页
- 【页面/场景 S-xx】S07 字典管理
- 【任务描述】实现 `dict.ts`：BE-API-26（按 `type=grade|absent_reason` 列表）、BE-API-27（新增）、BE-API-28（修改）、BE-API-29（删除）。
- 【可验证标准】函数签名对齐 BE-API-26~29；按 type 查询正确；类型检查通过。
- 【依赖】W1-07、W1-03

### W2-10 S07 字典管理页（年级 / 顺延原因）
- 【波次 Wave】Wave 2 管理页
- 【页面/场景 S-xx】S07 字典管理 `/admin/dicts`
- 【任务描述】`el-tabs` 分「年级」「顺延原因」两组；每组 `el-table` + 新增/编辑(`el-dialog`，`dictValue`/`sortOrder`/`enabled`)/删除。年级字典供 S01 年级下拉、顺延原因供 S05 顺延弹窗复用。
- 【可验证标准】两组字典独立维护；增删改即时反映；年级字典变更后 S01 下拉同步（经 W5-01 字典缓存）。
- 【依赖】W2-09、W1-06

---

## Wave 3 — 排课网格（S04）

> 后端依赖：Wave B（BE-API-15~20）。原型 `prototype/S04_排课网格.vue` 已验证交互，本波将其「晋升」为 `src/views/schedule` 并对接真实接口。

### W3-01 apis/lesson.ts（S04 部分）封装
- 【波次 Wave】Wave 3 排课网格
- 【页面/场景 S-xx】S04 排课系统
- 【任务描述】在 `lesson.ts` 实现 S04 接口：BE-API-15（当月网格 `?year=&month=` → `{days,slots,cells}`）、BE-API-16（拖拽建课 `studentId,slotId,lessonDate`）、BE-API-17（删课）、BE-API-18（改课 `slotId,lessonDate`）、BE-API-19（保存当月 `POST /save-month` → `{closedCount}`）、BE-API-20（按学生出图数据 `GET /student/{id}/export`）。
- 【可验证标准】签名对齐 BE-API-15~20；`cells` 结构 `{date#slotId: LessonCell}` 解析正确；类型检查通过。
- 【依赖】W1-07、W1-03

### W3-02 排课页脚手架：复用原型 → 晋升 src/views/schedule
- 【波次 Wave】Wave 3 排课网格
- 【页面/场景 S-xx】S04 排课系统 `/admin/schedule`
- 【任务描述】将 `prototype/S04_排课网格.vue` 的交互/视觉迁移为 `src/views/schedule/index.vue`，抽取 `ScheduleGrid.vue`（自绘 `<table>` + HTML5 拖拽，保留五态 loading/empty/error/populated/edge）。左侧学生列表（可搜索/拖拽，`el-color-picker` 着色）、右侧学生信息（`el-card`+`el-descriptions`）保留。组件库边界：网格自绘、外层用 `el-*`。
- 【可验证标准】页面渲染与原型一致：三栏布局、当月网格、五态展示；构建通过；无 `te-*`。
- 【依赖】W1-06、W3-01、W2-02

### W3-03 拖拽建课对接（每格 1 人 + 删课）
- 【波次 Wave】Wave 3 排课网格
- 【页面/场景 S-xx】S04 排课系统
- 【任务描述】拖入学生 → 调 BE-API-16 建课；每格 1 人冲突时后端返 `409`，前端 `ElMessage.warning` 实时拦截；拖出/点删调 BE-API-17。单元格着色用 `student.color` + 状态底色。
- 【可验证标准】拖入成功入格；同格再拖入被 `409` 拦截并提示「每格仅 1 人」；点删/拖出成功移除；网格与后端数据一致。
- 【依赖】W3-02

### W3-04 改课对接（换时段/日期）
- 【波次 Wave】Wave 3 排课网格
- 【页面/场景 S-xx】S04 排课系统
- 【任务描述】支持将已排课单元改到其它时段/日期（调 BE-API-18，body `slotId,lessonDate`）；目标格冲突 `409` 拦截。
- 【可验证标准】改课后单元移动到新格且后端更新；冲突目标格 `409` 提示且不移动。
- 【依赖】W3-03
- 【待确认】原型 U4（是否支持拖动已排课单元）：当前按「删除后重拖」实现；若需直接拖动已排单元，本任务需扩展拖拽源，待定。

### W3-05 保存当月对接（关闭上月顺延）
- 【波次 Wave】Wave 3 排课网格
- 【页面/场景 S-xx】S04 排课系统
- 【任务描述】点「保存」→ `ElMessageBox.confirm` 提示「将一并关闭上月顺延课」→ 调 BE-API-19 提交当月网格变更集；成功后展示 `closedCount`（上月顺延已关闭数）。仅当前月可保存，历史月禁用并提示只读。
- 【可验证标准】保存成功且提示 `closedCount`；历史月保存按钮禁用；提交结构为当月变更集、含 `lessonDate` 仅当月校验。
- 【依赖】W3-03、W3-04

### W3-06 按学生出图（html2canvas → PNG）
- 【波次 Wave】Wave 3 排课网格
- 【页面/场景 S-xx】S04 排课系统
- 【任务描述】「按学生出图」→ 选学生(`el-select`) → 调 BE-API-20 取该生当月课表 JSON → 渲染到隐藏/可见网格 → `html2canvas` 截取该生课表导出 PNG（发家长/打印）。状态底色在图中保留（PRD 7/8）。
- 【可验证标准】选学生后导出 PNG 文件下载成功，图中含该生当月排课与状态底色；无该生课表时提示。
- 【依赖】W3-02、W3-01
- 【待确认】原型 U1（出图版式：单学生当月 vs 整月）：当前按「单学生当月课表」实现，定版后再微调版式。

---

## Wave 4 — 前台看板（S09/S08/S10/S06 + S05 顺延标记）

> 后端依赖：Wave C（BE-API-21~25 顺延/补课）、Wave D（BE-API-30~32 统计）、Wave E（BE-API-33 + 复用 看板）。本波补齐剩余 apis 并实现 4 个前台页。

### W4-01 apis 补齐（lesson S05/S09 + makeUp + statistics）
- 【波次 Wave】Wave 4 前台看板
- 【页面/场景 S-xx】S05/S06/S08/S09
- 【任务描述】在 `lesson.ts` 补：BE-API-21（标记顺延 `absentBy,absentReason`，缺省 `400`）、BE-API-22（状态切换 `NORMAL/MADEUP/CANCELLED`）、BE-API-33（今日 `lessons`+`pendingToday`）。新建 `makeUp.ts`：BE-API-23（待补列表）、BE-API-24（安排补课 `makeUpDate`）、BE-API-25（手动关闭）。新建 `statistics.ts`：BE-API-30（月总览）、BE-API-31（历史明细）、BE-API-32（月收入）。
- 【可验证标准】签名对齐 BE-API-21~25、30~33；类型检查通过；`pendingToday` 仅含 `make_up_date=今天` 的待补。
- 【依赖】W1-07、W1-03

### W4-02 S09 今日视图页（落地页）+ 一键标记
- 【波次 Wave】Wave 4 前台看板
- 【页面/场景 S-xx】S09 今日视图 `/dashboard/today`（落地页）
- 【任务描述】复用 `prototype/S09_今日视图.vue` → 晋升 `src/views/dashboard/today`：`el-alert` 今日日期；今日课程 `el-table`（学生/时段/状态 `el-tag`）；「一键标记」`el-dialog`+`el-form`（`el-radio-group` 正常/顺延，`el-select` 请假方，`el-input` 原因，顺延必填请假方+原因）调 BE-API-21/22；顺带提示「今日该补的待补课」。
- 【可验证标准】落地页展示今日课程；标记顺延时未填原因被 `400` 拦截；填全后状态更新且底色变化；今日待补（`make_up_date=今天`）以提示条/卡片显示。
- 【依赖】W1-06、W4-01、W2-02

### W4-03 S06 待补课首页（无则不显示 + 手动关闭）
- 【波次 Wave】Wave 4 前台看板
- 【页面/场景 S-xx】S06 补课闭环 `/dashboard/make-up`
- 【任务描述】调 BE-API-23 拉全局待补（`status=ABSENT AND closed=0`）；无则 `el-empty` 隐藏列表（PRD 4.6「无则不显示」）；每条提供「已安排进本月课程」→ 调 BE-API-25 手动关闭（`ElMessageBox.confirm`）。同时该提示在 W4-02 落地页以提示条出现。
- 【可验证标准】有待补则列表展示、无则 `el-empty` 隐藏；点「已安排进本月课程」确认后该项从列表消失（仅 `closed=1`，`status` 保持 ABSENT）。
- 【依赖】W4-01、W4-02

### W4-04 S08 数据总览页（按月 + 月收入 + 顺延分类）
- 【波次 Wave】Wave 4 前台看板
- 【页面/场景 S-xx】S08 数据总览 `/dashboard/overview`
- 【任务描述】`el-date-picker`(月) 选择任意月；`el-card` 总览卡片（排 N/已上 X/顺延 Y 拆 学生请假 a+老师请假 b/已补 Z/作废 W/仍待补 K，调 BE-API-30）；`el-table` 历史月未上课明细（ABSENT 未补 + CANCELLED，BE-API-31）；月收入卡片（BE-API-32，口径 `(NORMAL+MADEUP)×price`）；顺延分类按 `absentBy` 区分。
- 【可验证标准】选月后卡片/明细/收入正确刷新；月收入口径与 Q2 一致；顺延分类学生/老师计数正确；历史月只读不写。
- 【依赖】W4-01、W1-06

### W4-05 S10 当月课程表页（复用网格管状态 + 历史月只读）
- 【波次 Wave】Wave 4 前台看板
- 【页面/场景 S-xx】S10 当月课程表 `/dashboard/monthly`
- 【任务描述】复用 W3-02 `ScheduleGrid.vue`（只读态）：读 BE-API-15；点单元格弹状态操作（正常/顺延填原因/已补/作废）调 BE-API-21/22；历史月（`lessonDate` 不在当前月）只读归档，禁用状态操作（或后端 `409`，前端禁用）。
- 【可验证标准】当月网格可管理状态；顺延标红显示原因；历史月网格只读、状态按钮禁用；与后台排课数据同源。
- 【依赖】W3-02、W4-01

---

## Wave 5 — 联调与验证（全量）

> 后端依赖：**Wave A~E 全部就绪并部署于 `47.116.35.76`**（见文末「对后端依赖」）。

### W5-01 跨页状态管理 store
- 【波次 Wave】Wave 5 联调验证
- 【页面/场景 S-xx】全局 `store/`
- 【任务描述】用 Pinia 维护跨页共享状态：当前选中月份、待补课实时计数、字典缓存（年级/顺延原因，供 S01 下拉与 S05 弹窗复用）。store 初始化自 BE-API-26/23。
- 【可验证标准】切换月份后相关页联动；增删待补后计数实时变化；字典缓存命中 S01/S05，无需重复请求。
- 【依赖】W4-01、W2-10

### W5-02 五态统一处理封装
- 【波次 Wave】Wave 5 联调验证
- 【页面/场景 S-xx】全局 `utils/useViewState.ts` 或 `components/ViewState.vue`
- 【任务描述】将原型中重复的 loading/empty/error/populated/edge 五态逻辑抽取为通用组合式/`el-skeleton`+`el-result`+`el-empty` 封装，各页复用，避免散落。
- 【可验证标准】各页五态展示一致；通用封装被 S04/S09/S08 等复用；构建通过。
- 【依赖】W3-02、W4-02、W4-04

### W5-03 后端契约全量联调
- 【波次 Wave】Wave 5 联调验证
- 【页面/场景 S-xx】全量 BE-API-01~33
- 【任务描述】逐一对接 BE-API-01~33 真实后端（代理指向 `47.116.35.76`）；验证分页 `{list,total}`、统一返回体、五态处理、错误码（400/409）在各页表现；核心域规则（每格 1 人、重叠校验、删除保护、保存关闭上月顺延、月收入口径）端到端跑通。
- 【可验证标准】9 个页面在真实后端下功能正常；33 条接口全部被调用且行为符合后端概设 §2.4；冲突/保护场景提示准确。
- 【依赖】W2（全）、W3（全）、W4（全）、W1-03、W1-04

### W5-04 状态机五态标签/底色全局一致
- 【波次 Wave】Wave 5 联调验证
- 【页面/场景 S-xx】全局 `constants/status.ts`（STATUS_META）
- 【任务描述】抽取共享 `STATUS_META`（UNTAKEN/NORMAL/ABSENT/MADEUP/CANCELLED 的 label/bg/fg/el-tag type），供 S04 网格、S09 今日视图、S10 课程表、S08 明细统一引用，避免各页重复定义导致不一致。
- 【可验证标准】五态在网格/今日/课程表/总览中底色与文案完全一致；修改一处全局生效。
- 【依赖】W3-02、W4-02、W4-04、W4-05

### W5-05 构建验证
- 【波次 Wave】Wave 5 联调验证
- 【页面/场景 S-xx】全局
- 【任务描述】执行 `npm run build` 与 `vue-tsc` 类型检查（原型期仅 `vite build` 未跑 `vue-tsc`，见 `_notes.md` 5.1）；修复全部类型/构建错误；确认无 `te-*` 引用、无 `src/` 之外的业务代码泄漏。
- 【可验证标准】`npm run build` 成功产出 `dist/`；`vue-tsc` 零类型错误；全量自检清单（下文）通过。
- 【依赖】W5-01~W5-04

---

## 对后端依赖（Wave 5 联调前需就绪）

前端 Wave 5 联调必须等后端对应波次完成并部署于 `47.116.35.76`（Q1）。建议后端波次对照（依据后端概设 S-xx）：

| 后端 Wave | 覆盖 S-xx | 接口（BE-API） | 支撑前端波次 |
|---|---|---|---|
| A 基础数据 | S01/S02/S03/S07 | 01~14、26~29 | Wave 2 |
| B 排课核心 | S04 | 15~20 | Wave 3 |
| C 顺延/补课 | S05/S06 | 21~25 | Wave 4（W4-02/03） |
| D 统计 | S08 | 30~32 | Wave 4（W4-04） |
| E 看板 | S09/S10 | 33 + 复用 | Wave 4（W4-02/05） |

> 阻塞关系：Wave 2→后端 A；Wave 3→后端 B；Wave 4→后端 C/D/E；Wave 5→后端 A~E 全部。各前端波次在后端对应接口未就绪时，可先以 mock（如原型 Promise）跑通 UI，但 Wave 5 必须以真实后端收口。

## 待确认项（不阻塞拆解，落地实现前需定版）

1. **S02 `time_slot.name`**：后端 §2.3 未定义该字段（前端概设 §2 标注「建议，待确认」）。影响 W2-06 表单字段。
2. **U1 出图版式**：单学生当月 vs 整月（影响 W3-06）。
3. **U2 「已安排进本月课程」精确动作**：当前定义「仅关闭待补项」，实际补课由教师另拖网格（影响 W4-03 与 BE-API-25 语义）。
4. **U3 跨多月遗留待补**：仅靠首页手动关闭是否足够（影响 W4-03 范围）。
5. **U4 网格拖动已排课单元**：当前仅「删除后重拖」（影响 W3-04 拖拽实现）。
6. **Q17 MySQL 连接信息**：库名/账号/密码待补，不阻塞前端，但 Wave 5 联调前需后端可达 `47.116.35.76`。

## 自检

- [x] 任务含【波次 Wave】【页面/场景 S-xx】【任务描述】【可验证标准】【依赖】五要素
- [x] 粒度=最小可验证改动（脚手架/路由/列表/表单/接口对接各为任务）
- [x] 波次按依赖排序 Wave 1~5，与建议一致
- [x] 覆盖全部 S01~S10 与 BE-API-01~33（前端侧封装）
- [x] 标注对后端 Wave A~E 依赖与待确认项
- [x] 未写 `src/`/`frontend/` 实现代码，仅本任务文档
- [x] 组件库约束 `el-*`、禁 `te-*` 在硬约束中声明
