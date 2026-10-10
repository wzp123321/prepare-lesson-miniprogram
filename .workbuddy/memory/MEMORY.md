# 备课排课管理系统 —— 项目长期记忆

## 本机环境（2026-10-08 实测）
- JDK 21 + Maven 3.9.16 + MySQL 8.0.46 客户端齐备，后端可真编译真跑，勿再假定"本机无 JDK"。
- 本机 MySQL root/123456，库 lesson_db 已建（student/course/lesson/time_slot/dict）。可用：export MYSQL_PWD=123456; mysql -h127.0.0.1 -uroot -Dlesson_db
- 后端默认端口 18899；常驻实例需重启才吃新配置。后端接口统一 POST：/api/dicts/list|create|update|delete、/api/time-slots/list|create|update|sort|delete。
- 约定：enabled 字段一律 Integer 1/0（DB TINYINT），前端发 1/0，el-switch 必须写 :active-value="1" :inactive-value="0"。
- backend/db/schema.sql 幂等：建表 IF NOT EXISTS + 字典种子 NOT EXISTS 判重，可重复执行；
  文件末尾有「结构迁移」段（SET @col_exists + PREPARE 动态 DDL），给旧库幂等补列用。
- **字段归属**：`lesson.remark` = 排课备注；`lesson.prep_remark` = 备课备注。两者已拆开，不要再混用同一列。
- **时段顺序唯一规则**：全站按 `time_slot.start_time` 升序（时间段管理页、排课网格、出图、今日课）。
  `sort_order` 列仍在库里但不再决定任何顺序，排序端点 /time-slots/sort 已删除。
- **课程状态**：启用/停用走 `/api/courses/enable` 与 `/api/courses/disable`（不要再用 update-remark 代偿）。
- **排查坑**：GlobalExceptionHandler 会把「路径不存在」包成 `code:500` + **HTTP 200**
  （body 文案 `No static resource ...`）。验证端点是否真的删除/存在时，**不能只看 HTTP 状态码，必须读响应体**。
- 前端工具函数位置：错误处理 `utils/error.ts`（errMsg/isConflict）、颜色分配 `utils/color.ts`（autoColor）；
  `api/mockData.ts` 现在只放 mock 数据，不要再往里面塞工具函数。

## 智能排课（AI）模块（2026-10-08 定型）
- 模型：**DeepSeek**，经 Spring AI 的 **OpenAI 兼容协议**接入 —— `org.springframework.ai:spring-ai-starter-model-openai:1.0.0`。
  ⚠️ 不要改用 `spring-ai-starter-model-deepseek`：其 1.0.0 客户端没有 thinking 参数，而 DeepSeek V4 默认开思考模式，
  思考模式 + 工具调用要求回传 reasoning_content（Spring AI 不回传）→ 工具调用第二轮必 400。
- 配置（application.yml）：`spring.ai.openai.{api-key, base-url=https://api.deepseek.com,
  chat.completions-path=/chat/completions, chat.options.model=deepseek-flash, chat.options.reasoning-effort=none}`，
  并显式关闭 embedding / image / audio / moderation 自动配置。模型名当前用 `deepseek-flash`（`deepseek-chat` 已弃用）。
- API Key：`spring.ai.openai.api-key`，可直接写在配置里，也可用环境变量 `AI_DEEPSEEK_API_KEY` 覆盖；
  未配置时给占位值 `not-configured`（空值会让整站启动失败），业务层判断后只让 AI 接口报错，不影响其他功能。
- 架构：两阶段（chat 只出方案不落库 → 老师勾选 → apply 执行）；查询走 `AiScheduleTools` 的 `@Tool` 由模型自主调用，
  写操作走结构化 actions。多轮对话**无状态**（前端回传最近 8 条 history，无 ChatMemory、无新表）。
- 前端入口：排课页工具条「智能排课」按钮 + **全站右下角悬浮球**，两者都打开同一个抽屉
  （`components/AiScheduleFab.vue`，挂在 `AdminLayout`）。后端基于 Spring Boot 3.4.0。

## 系统定位与边界（2026-10-08 用户明确）
- **纯单人自用**：使用者只有老师本人。**不涉及家长**；不做多用户、登录、权限、消息推送。
- 据此裁定：课后反馈发家长 → **不做**；给家长的课前提醒 → **不做**；
  「续费/课时不足提醒」降级为"提醒老师自己"，并入课时台账需求。
- 功能取舍一律以「帮老师自己省事、记账、少出错、随时查得到」为准；"给外人看的体面"不算需求。
- 「按学生出图 PNG」「整月导出 Excel」保留，但定位是老师自己打印/存档，不再是家长沟通素材。
- 单人单机无云无协作方兜底 → **数据备份/恢复优先级最高**（库在本地 MySQL，无任何备份机制）。

## 前端目录与全局状态约定（2026-10-08）
- 目录：`layouts/`（布局）、`views/`、`api/`、`utils/`、`components/`（本次新建）。
- 全局单例状态直接放 `utils/*.ts` 的模块级 ref（如 `utils/aiDrawer.ts`），**未引入 Pinia**。
- AI 抽屉开合与刷新信号都在 `utils/aiDrawer.ts`：`openAiDrawer(month?)` 打开，
  AI 动作落库后 `aiAppliedTick + 1`，排课页 `watch` 它重载网格。
- 聊天 UI 用 Element Plus 打底（el-drawer / el-input / el-button / el-checkbox），
  消息气泡与动作卡片是项目自绘 CSS，没有引专门聊天组件库。

## 题库 / 试卷 / 打印（2026-10-09 交付）
- **组卷是两步，不要再一步落库**：`POST /api/papers/generate/preview`（只试抽不落库，
  返回 `PaperGeneratePreviewVO{questions,total,picks[]}`）→ 老师预览/换题 → `POST /api/papers/generate/commit`
  （按 `questionIds` 建卷+编排一步完成）。旧的 `POST /generate` 保留但前端已不用。
- `PaperGenerateDTO` 的题量给法：`kpCounts:[{kpId,count}]` **优先**，`countPerKp` 是兼容兜底；
  `maxTotal` 是整卷上限。**按点设量**是用户认的交互，别再退回"一个数管所有知识点"。
- `picks` 里的 `wanted/available/picked` 要让前端**如实显示题量不足**（"想抽 10 题、题库仅 3 题"），
  不要静默少给。
- **打印走独立整页**：`/print/paper/:id` → `PaperPrintView.vue`，**挂在 AdminLayout 之外**（顶层路由，无侧栏）。
  浏览器打印 + `@media print` + `@page A4 portrait`，不引任何打印库。
- **卷面渲染约定**：按题型分大题，顺序由 **`frontend/src/utils/qtype.ts` 的 `QTYPE_ORDER`** 决定
  （`constants/question.ts` 只留 `QTYPES`/`DIFFICULTY_LABEL`；打印页与制卷台共用 `qtypeWeight()`）
  →「一、选择题（共 N 小题）」；`.big-q`/`.q` 必须带 `break-inside: avoid`。
- **只做学生版**：打印/预览**不渲染 answer/analysis**（后端会返回，前端有意不画）；
  作答留白按题型区分（选择/填空/判断 1 行、阅读/古诗文 3 行、写作空白框）。
- 打开打印页一律 `router.resolve({name:'PaperPrint',params:{id}})` + `window.open`（新标签，不丢列表状态）。
- 录题抽屉：**`kpId` 不必填**（留空可存，但组卷抽不到）；知识点下拉按 `category` 分组（el-option-group）；
  判断题选项固定「正确/错误」；答案支持**点字母**多选（拼成 "AB"）且保留手输；题干输入触发查重提示。
- `paper_question` 是**纯引用表**：改一道题会影响所有引用它的卷（只有 `clone` 会复制题目行）。UI 上要给警告。

## 制卷台（2026-10-10 交付，改动请沿用这套定式）
- **独立整页** `/prep/compose/:id` → `PaperComposeView.vue`，**挂 AdminLayout 之外**（顶层路由，无侧栏）；
  试卷列表页「制卷」按钮跳这里，列表页**不再有编排抽屉**。
- 三栏 `grid 300px | 1fr | 260px`，整页 `100vh`；左=试题库（筛选+卡片拖出）、中=卷面、右=组卷信息。
- **拖拽用 `vuedraggable`**（已在 `package.json`）：`group={name:'compose-questions',pull:'clone',put:true}`；
  题库→卷面是克隆、卷面内是移动；行 `handle=".drag-handle"`（左侧 `⠿`）；**可拖到任意 index**（不限本大题内）。
- **行模型 `ComposeRow`**：阅读/古诗文 2 小题并排一行（`isPairedType`），其余一题一行；
  行内小题各占**独立连续题号**（`numberAt(rowIndex,qi)`）。并排/拆开走**行级按钮** ——
  **不要回到"落点吸并"**（隐式、不可控，已弃用）。
- **自动保存**：变动 → 防抖 500ms → `POST /papers/questions/set` 整体覆盖；顶部显示保存态；
  `Ctrl/Cmd+S` 立即存；`onBeforeUnmount` 补存。**不要加逐题「保存」按钮**（竞品缺陷，用户明确不学）。
- 卷面拍平顺序 `flatQuestions()` **= 打印页题号顺序**（所见即所得）。
- 竞品取舍已定：抄「左题右卷 + 拖拽排版 + 弹层看题 + 已入卷置灰」；
  **不抄**装订线/答题卡/双栏/分值细目表、逐题保存、拍照匹配题库、学情驱动组卷（无数据源）。

## 批量导入录题（2026-10-10 交付）
- 后端 `POST /api/questions/check-duplicates`（BE-Q-06）：入参 `stems[]`，返回同序 `{dupId,dupStem,exact}`。
  归一化**必须剥行首题号**（`1.`/`1、`/`一、`），否则假阴性；前缀检索用前 8 字；归一化后 < 4 字不参与匹配。
- `batchCreate` 已含**批内去重**（同批归一化相同只入第一条）。
- 导入弹窗核对区是**结构化表格**（勾选/题型/题干/答案/知识点/难度），有**体检条**（缺答案/缺知识点/疑似重复计数）；
  行 key 必须用 **uid 不用下标**（否则输入框串行）；与题库完全相同的题默认不勾选。

## 批量导入录题（2026-10-10 交付）
- 后端 `POST /api/questions/check-duplicates`（BE-Q-06）：入参 `stems[]`，返回同序 `{dupId,dupStem,exact}`。
  归一化**必须剥行首题号**（`1.`/`1、`/`一、`），否则假阴性；前缀检索用前 8 字；归一化后 < 4 字不参与匹配。
- `batchCreate` 已含**批内去重**（同批归一化相同只入第一条）。
- 导入弹窗核对区是**结构化表格**（勾选/题型/题干/答案/知识点/难度），有**体检条**（缺答案/缺知识点/疑似重复计数）；
  行 key 必须用 **uid 不用下标**（否则输入框串行）；与题库完全相同的题默认不勾选。

## 卷内编辑（试卷内容覆盖）与一键复用（2026-10-10 定型，当日二次改版）
- ⚠️ **不要用"快照"这个词/这种列结构**：用户明确否决在 `paper_question` 上摊 `snap_*` 宽列（嫌表宽）。
  定案 **edited 标记位 + override 独立表**：
  - `paper_question` **只有 5 列**（`id/paper_id/question_id/sort_order/edited`）。
  - `paper_question_override`（一对一，`paper_question_id` 唯一）存被编辑过的题面：`qtype/stem/options/answer/analysis/difficulty`。
  - 语义：`edited=0` 读 question 表；`edited=1` 读 override 表（查不到回落题库）；改卷**不影响题库与其他卷**。
- 后端出口唯一 `PaperServiceImpl.toQuestionVOsFromLinks()`；`copyOverride(from,to)` 供 clone/reuse 复制；
  `setQuestions` 整体覆盖要**先删旧 override 再删编排项**（否则留孤儿行）。
- 前端 `api/paper.ts` 的 `PaperQuestionItem` 是**平铺字段**（qtype/stem/...），后端 DTO `Item` 同名——契约稳定，制卷台无需关心存储形态。
- **否决 A 方案（只加标记+编辑直接改题库）的硬证据**：`PrepServiceImpl.suggest`（备课页智能推荐）直接查 question 表按 kpId 捞通用题、
  绕过试卷编排；若编辑直接改题库，备课推荐会被静默污染。单人自用无人提醒 → 必须隔离。
- **一键复用** `POST /api/papers/reuse {sourceId,title?,studentId?}`：以蓝本卷生成**新的可编辑卷**，
  **不克隆题库行**，只复制编排顺序 + override；studentId 不传沿用蓝本；新卷 parentPaperId=源卷、status=DRAFT。
- 制卷台入口：顶部「从已有试卷导入」（选中卷题目**追加**到当前卷末尾，重复跳过）+「整卷复用」（另存新卷）；列表页操作列「复用」。
- **UI 偏好**：用户明确**反感"AI 味"模板感**，制卷/工作台类页面要专业编辑台质感——
  `tokens.css` 已备 `--grad-toolbar/--grad-brand(-soft)/--paper-bg/--canvas-bg/--canvas-grad/--ring-brand/--hairline/--shadow-raise/--shadow-float`。
  卷面用"白纸浮灰底"+`--shadow-float`；按钮改自绘 `.op-btn/.side-btn/.qtype-chip` 代替裸 el-button link。
  ⚠️ **UI 升级目前只覆盖制卷模块**（制卷台/试卷列表/打印页）；题库页、知识点页、备课页尚未升级（用户未确认，勿擅自推）。

## 今日待办（2026-10-09 交付）
- **系统落地页已改为 `/dashboard/todo`（今日待办）**，不再是今日视图；兜底路由同步改。
- 后端 `POST /api/dashboard/today-todo`（BE-API-35，无入参，只读聚合）：四类
  MARK_TODAY / MAKEUP_TODAY / MAKEUP_OVERDUE / PREP_TODAY，共用 `TodoItemVO` 行结构 + `TodayTodoVO` 外壳。
- 待办页**只指路不处理**：整行点击跳对应页面并带 `?studentId=`，写操作仍走各模块原接口。
- 备课页（PrepTodoView）的优化定式：**默认隐藏已上完的课 + 待备/已备分组 + 知识点按大类折叠搜索
  + 「保存并备下一节」流水线**。这些是用户认可的交互方向，后续改备课页请沿用。

## 构建与冒烟（2026-10-09 实测补充）
- `mvn package` 在本机离线环境会因 maven-surefire-plugin 插件容器异常失败；
  改用 `mvn -o spring-boot:run` 起临时实例即可正常跑（`target/classes` 已编译）。
- 起临时实例用 `SERVER_PORT=18888 spring-boot:run`，**不要动用户的 18899**；测完 taskkill 并按 PID 确认。
- 冒烟造数据注意 `lesson` 有唯一约束 `uk_lesson_date_slot`（同日期同时段只能一条），
  批量 INSERT 撞约束会中断整批，需换空闲格子。
- **前端 dist 重建**：就地覆盖 `dist` 会反复构建失败/超时，先 `mv dist dist-old-$(date +%H%M%S)` 再 build。
- **`vite preview` 在沙箱里验不了**：只绑 `[::1]`，curl 直连 loopback 被拦（exit 27）。
  要验页面行为，改为验**数据源**（对应 REST 接口的响应字段），或起 `SERVER_PORT=18888` 的真后端。
- 前端改动后的标准三连：`npx vue-tsc --noEmit` → `npx vite build` → 抽查产物里关键 CSS 规则是否存在。

