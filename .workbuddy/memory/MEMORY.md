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

