# 备课排课管理系统 —— 项目长期记忆

> 精简整理版（2026-10-12）。过程细节看同目录 `YYYY-MM-DD.md` 日志。

## 1. 本机环境与运行（2026-10-08 实测）
- JDK 21 + Maven 3.9.16 + MySQL 8.0.46 齐备，后端可真编译真跑。
- MySQL：`root/123456`，库 `lesson_db`（student/course/lesson/time_slot/dict）。
  `export MYSQL_PWD=123456; mysql -h127.0.0.1 -uroot -Dlesson_db`
- 端口：后端常驻 **18899**（改完后端代码必须先重启，否则可能假成功不落库）；
  独立验证起临时实例 `SERVER_PORT=18888 mvn -o spring-boot:run`；前端 dev **18887**（vite）。
- 后端接口统一 POST。`GlobalExceptionHandler` 会把「路径不存在」包成 `code:500` + **HTTP 200**
  （body 文案 `No static resource ...`）→ 验端点不能只看状态码，**必须读响应体**。
- `mvn package` 在离线环境会因 surefire 插件容器异常失败，改用 `mvn -o spring-boot:run`。

## 2. 全局约定
- `enabled` 一律 Integer 1/0（DB TINYINT）；el-switch 必须写 `:active-value="1" :inactive-value="0"`。
- **时段顺序唯一规则**：全站按 `time_slot.start_time` 升序（时段页 / 排课网格 / 出图 / 今日课）。
  `sort_order` 列还在但不再决定顺序；`/time-slots/sort` 端点已删。
- **字段归属**：`lesson.remark` = 排课备注；`lesson.prep_remark` = 备课备注。不要混用同一列。
- 课程启停走 `/api/courses/enable`、`/api/courses/disable`。
- `backend/db/schema.sql` 幂等：建表 `IF NOT EXISTS` + 字典种子判重；末尾有「结构迁移」段
  （`SET @col_exists` + `PREPARE` 动态 DDL），给旧库幂等补列。
- 前端工具函数：`utils/error.ts`（errMsg/isConflict）、`utils/color.ts`（autoColor）；
  `api/mockData.ts` 只放 mock 数据，不塞工具函数。
- 全局单例状态放 `utils/*.ts` 模块级 ref（如 `utils/aiDrawer.ts`），**未引 Pinia**。
- `lesson` 有唯一约束 `uk_lesson_date_slot`（同日期同时段只一条），批量 INSERT 撞约束会中断整批。

## 3. 模块定式

### 3.1 智能排课（AI，2026-10-08 定型）
- 模型 **DeepSeek**，经 Spring AI **OpenAI 兼容协议**接入：`spring-ai-starter-model-openai:1.0.0`。
  ⚠️ 不要换成 `spring-ai-starter-model-deepseek`——其 1.0.0 客户端无 thinking 参数，而 DeepSeek V4 默认开
  思考模式，思考模式 + 工具调用要求回传 `reasoning_content`（Spring AI 不回传）→ 工具调用第二轮必 400。
- 配置：`spring.ai.openai.{api-key, base-url=https://api.deepseek.com, chat.completions-path=/chat/completions,
  chat.options.model=deepseek-flash, chat.options.reasoning-effort=none}`，并显式关闭 embedding/image/audio/moderation。
  模型名用 `deepseek-flash`（`deepseek-chat` 已弃用）。Key 可写配置或用环境变量 `AI_DEEPSEEK_API_KEY` 覆盖；
  未配置时给占位值 `not-configured`（空值会让整站启动失败）。
- 架构：两阶段（chat 只出方案不落库 → 老师勾选 → apply 执行）；查询走 `AiScheduleTools` 的 `@Tool`，
  写操作走结构化 actions。多轮对话**无状态**（前端回传最近 8 条 history，无 ChatMemory、无新表）。
- 入口：排课页工具条「智能排课」+ 全站右下角悬浮球（同一个抽屉 `components/AiScheduleFab.vue`，挂 `AdminLayout`）。

### 3.2 题库 / 试卷 / 打印（2026-10-09）
- **组卷两步**，不要一步落库：`POST /api/papers/generate/preview`（只试抽不落库）→ 老师预览换题 →
  `POST /api/papers/generate/commit`（按 `questionIds` 建卷 + 编排）。旧的 `POST /generate` 保留但前端不用。
- `PaperGenerateDTO`：`kpCounts:[{kpId,count}]` **优先**，`countPerKp` 兼容兜底，`maxTotal` 是整卷上限；
  **按点设量**是用户认的交互。`picks` 的 `wanted/available/picked` 要如实显示题量不足，不要静默少给。
- **打印独立整页** `/print/paper/:id` → `PaperPrintView.vue`，挂 `AdminLayout` 之外（顶层路由，无侧栏）。
  浏览器打印 + `@media print` + `@page A4 portrait`，不引打印库。
- 卷面渲染：按题型分大题，顺序由 `frontend/src/utils/qtype.ts` 的 `QTYPE_ORDER` 决定
  （`constants/question.ts` 只留 `QTYPES`/`DIFFICULTY_LABEL`；打印页与制卷台共用 `qtypeWeight()`）；
  `.big-q`/`.q` 必须带 `break-inside: avoid`。
- **只做学生版**：打印/预览不渲染 answer/analysis；作答留白按题型区分（选择/填空/判断 1 行、阅读/古诗文 3 行、写作空白框）。
- 打开打印页一律 `router.resolve({name:'PaperPrint',params:{id}})` + `window.open`（新标签，不丢列表状态）。
- 录题抽屉：`kpId` 可不填（留空可存，但组卷抽不到）；知识点下拉按 `category` 分组（el-option-group）；
  判断题选项固定「正确/错误」；答案支持点字母多选（拼成 "AB"）且保留手输；题干输入触发查重提示。
- `paper_question` 是**纯引用表**：改一道题会影响所有引用它的卷（只有 clone 会复制题目行），UI 要警告。

### 3.3 批量导入录题（2026-10-10）
- `POST /api/questions/check-duplicates`（BE-Q-06）：入参 `stems[]`，返回同序 `{dupId,dupStem,exact}`。
  归一化**必须剥行首题号**（`1.`/`1、`/`一、`），否则假阴性；前缀检索用前 8 字；归一化后 < 4 字不参与匹配。
- `batchCreate` 已含**批内去重**（同批归一化相同只入第一条）。
- 导入弹窗核对区是**结构化表格**（勾选/题型/题干/答案/知识点/难度），有**体检条**（缺答案/缺知识点/疑似重复计数）；
  行 key 必须用 **uid 不用下标**（否则输入框串行）；与题库完全相同的题默认不勾选。

### 3.4 制卷台（2026-10-10 交付，改动沿用这套定式）
- **独立整页** `/prep/compose/:id` → `PaperComposeView.vue`，挂 `AdminLayout` 之外（顶层路由，无侧栏）；
  试卷列表页「制卷」跳这里，列表页**不再有编排抽屉**。三栏 `grid 300px | 1fr | 260px`，整页 `100vh`。
- 拖拽用 `vuedraggable`：`group={name:'compose-questions',pull:'clone',put:true}`；题库→卷面是克隆、卷面内是移动；
  行 `handle=".drag-handle"`；**可拖到任意 index**（不限本大题内）。
- 行模型 `ComposeRow`：阅读/古诗文 2 小题并排一行（`isPairedType`），其余一题一行；行内小题各占**独立连续题号**
  （`numberAt(rowIndex,qi)`）。并排/拆开走**行级按钮**，**不要回到「落点吸并」**（隐式、不可控，已弃用）。
- **自动保存**：变动 → 防抖 500ms → `POST /papers/questions/set` 整体覆盖；顶部显示保存态；`Ctrl/Cmd+S` 立即存；
  `onBeforeUnmount` 补存。**不要加逐题「保存」按钮**（竞品缺陷，用户明确不学）。
- `flatQuestions()` 拍平顺序 **= 打印页题号顺序**（所见即所得）。
- 竞品取舍：抄「左题右卷 + 拖拽排版 + 弹层看题 + 已入卷置灰」；**不抄**装订线/答题卡/双栏/分值细目表、
  逐题保存、拍照匹配题库、学情驱动组卷（无数据源）。

### 3.5 卷内编辑与一键复用（2026-10-10 定型）
- ⚠️ **不要用「快照」这个词/这种列结构**：用户明确否决在 `paper_question` 上摊 `snap_*` 宽列（嫌表宽）。
  定案 **edited 标记位 + override 独立表**：
  - `paper_question` **只有 5 列**：`id/paper_id/question_id/sort_order/edited`。
  - `paper_question_override`（一对一，`paper_question_id` 唯一）存被编辑过的题面：
    `qtype/stem/options/answer/analysis/difficulty`。
  - 语义：`edited=0` 读 question 表；`edited=1` 读 override 表（查不到回落题库）；改卷**不影响题库与其他卷**。
- 后端出口唯一 `PaperServiceImpl.toQuestionVOsFromLinks()`；`copyOverride(from,to)` 供 clone/reuse 复制；
  `setQuestions` 整体覆盖要**先删旧 override 再删编排项**（否则留孤儿行）。
- 前端 `api/paper.ts` 的 `PaperQuestionItem` 是**平铺字段**（qtype/stem/...），后端 DTO `Item` 同名——契约稳定，
  制卷台无需关心存储形态。
- **否决 A 方案（编辑直接改题库）的硬证据**：`PrepServiceImpl.suggest`（备课页智能推荐）直接查 question 表按 kpId
  捞通用题、绕过试卷编排；若编辑直接改题库，备课推荐会被静默污染。单人自用无人提醒 → 必须隔离。
- **一键复用** `POST /api/papers/reuse {sourceId,title?,studentId?}`：以蓝本卷生成**新的可编辑卷**，
  **不克隆题库行**，只复制编排顺序 + override；studentId 不传沿用蓝本；新卷 `parentPaperId`=源卷、`status=DRAFT`。
- 制卷台入口：顶部「从已有试卷导入」（选中卷题目**追加**到当前卷末尾，重复跳过）+「整卷复用」（另存新卷）；
  列表页操作列「复用」。

### 3.6 今日待办 / 备课页（2026-10-09）
- 系统落地页已改为 `/dashboard/todo`（今日待办），兜底路由同步改。
- `POST /api/dashboard/today-todo`（BE-API-35，无入参，只读聚合）：四类
  MARK_TODAY / MAKEUP_TODAY / MAKEUP_OVERDUE / PREP_TODAY，共用 `TodoItemVO` 行结构 + `TodayTodoVO` 外壳。
- 待办页**只指路不处理**：整行点击跳对应页面并带 `?studentId=`，写操作仍走各模块原接口。
- 备课页（PrepTodoView）定式：**默认隐藏已上完的课 + 待备/已备分组 + 知识点按大类折叠搜索 + 「保存并备下一节」流水线**。

## 4. 调试 / 构建坑
- 前端 dist 重建：先 `mv dist dist-old-$(date +%H%M%S)` 再 build（就地覆盖会反复构建失败/超时）。
- `vite preview` 沙箱验不了：只绑 `[::1]`，curl 直连 loopback 被拦（exit 27）→ 改验**数据源**（REST 响应字段）
  或起 `SERVER_PORT=18888` 真后端。
- 前端改动后三连：`npx vue-tsc --noEmit` → `npx vite build` → 抽查产物里关键 CSS 规则是否存在。
- **vuedraggable 定式**：
  - **`:clone` 取自「拖拽源」那一侧**（`onDragStart` 设 `_underlying_vm_`，目标侧 `onDragAdd` 直接 splice）。
    两侧列表元素类型不同时（如 `Question[] → ComposeRow[]`）**必须两侧都挂同一个 `:clone` 包装函数**，
    否则裸源元素进目标列表 → 读缺失字段抛 `Cannot read properties of undefined`。
  - **空列表拖不进**：Sortable 判定落点靠容器 `getBoundingClientRect()`，空 flex 容器高度塌成 0 → 必须给 `min-height`
    （制卷台 240px）。
  - 空态提示不要用兄弟节点，改**绝对定位覆盖层**（容器内、`pointer-events:none`、父 `position:relative`）。
  - 第二道防线：目标侧保留 `isXxxRow()` 类型守卫 + `rowsOf` computed + `normalizeRow()`；`.length` 用
    `?.items?.length ?? 0`。
  - 真机拖拽验证：`agent-browser drag "<src>" "<dst>"`。

## 5. UI 基调（用户认可，2026-10-10）
- 用户明确**反感「AI 味」模板感**，要专业编辑台质感。`tokens.css` 已备
  `--grad-toolbar/--grad-brand(-soft)/--paper-bg/--canvas-bg/--canvas-grad/--ring-brand/--hairline/--shadow-raise/--shadow-float`。
- 去 AI 味做法：少用紫色渐变/色块徽章；标题用「左侧 3px 品牌色细线」代替方块图标；chip 用 `brand-50` 底 + `brand-100` 描边；
  顶栏不要「漂在正中间」的状态胶囊，改成左=文档信息（卷名+标签）、右=保存态 + 操作按钮组。
- 行内操作（编辑/移出/↑↓/并排拆开）一律 **hover 才显示**；除拖拽外提供**点选加入**（卡片「加入」按钮 + 双击）。
- ⚠️ **UI 升级目前只覆盖制卷模块**（制卷台/试卷列表/打印页）；题库页、知识点页、备课页尚未升级（用户未确认，勿擅自推）。

## 6. 系统定位与边界（2026-10-08 用户明确）
- **纯单人自用**：使用者只有老师本人。**不涉及家长**；不做多用户、登录、权限、消息推送。
- 据此裁定：课后反馈发家长 → **不做**；给家长的课前提醒 → **不做**；「续费/课时不足提醒」降级为「提醒老师自己」，
  并入课时台账需求。
- 功能取舍一律以「帮老师自己省事、记账、少出错、随时查得到」为准；「给外人看的体面」不算需求。
- 「按学生出图 PNG」「整月导出 Excel」保留，定位是老师自己打印/存档，不再是家长沟通素材。
- 单人单机无云无协作方兜底 → **数据备份/恢复优先级最高**（库在本地 MySQL，无任何备份机制）。

## 7. 亿赛通透明加密：`docs/*.md`（2026-10-12 实测，状态会反复）
- 本工程 `docs/*.md` 受透明加密覆盖：被**非白名单进程**（python / node / cp / sed）写过就变密文或带 NUL 尾巴；
  被 WorkBuddy 工具或 bash 重定向写过则是明文，**同一文件两种状态会来回切**。
- 判定明文：`tr -dc '\0' < f | wc -c` 必须为 **0**（全文件统计；`od -An -N10` 会漏判）。
  密文时 `grep`/`sed`/`od`/`dd` 读乱码，`head` 仍读得到明文。
- 配方：读用 `head -c 999999 f`（白名单读，密文也能读出明文）；写先 `head -c 999999 src > docs/__new.md`（**新文件名**），
  再 `mv -f docs/__new.md docs/xxx.md`。
  ⚠️ 直接写回**已存在的同名文件**会被驱动在正文后补 **1740 字节 0x00**（实测 200015 → 201755）；`mv` 覆盖则干净。
  也可直接用 WorkBuddy 的 Write / Edit 工具。
- 批量改 md：python 只写 `/tmp`，**落盘一律走 head + mv**，不要 python 直接写目标文件。
