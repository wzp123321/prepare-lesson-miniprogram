# 提交变更清单（卡点④ — 用户自行提交）

> 生成于 SDD 全流程收尾。用户选择「我自己提交，你给清单」，故本文件为变更清单与提交建议；编排者未执行任何 git 操作。
> 全部为**新增文件**（全新项目），无历史改动。

---

## 0. 提交前置（必做，防止误提交）

- [ ] 加 `.gitignore`，排除：`node_modules/`、`dist/`、`target/`、`*.class`、`.idea/`、`.vscode/`、`.workbuddy/`（若不想入库过程痕迹）。
- [ ] 确认 `backend/src/main/resources/application.yml` 中 DB 密码为环境变量占位（`DB_HOST/DB_NAME/DB_USER/DB_PASSWORD`），**不提交明文密码**（Q17）。
- [ ] 后端未在本机编译运行（沙箱无 JDK/Maven）。建议提交前 `cd backend && mvn compile` 验证（需 JDK21 + 联网拉 `spring-boot-starter-validation` 等依赖）。

---

## 1. 整体范围

| 分组 | 目录 | 说明 |
|---|---|---|
| A | `backend/` | Spring Boot 3.2.5 + MyBatis-Plus 3.5.7，33 个 BE-API |
| B | `frontend/` | Vue3 + Vite + TS + Element Plus，7 个 api 模块 `USE_MOCK=false` |
| C | `archive-spec/` `prototype/` | 后端/前端概设、UI 原型 SFC |
| D | `.sdd/` | 全流程过程痕迹（state/tasks/checklist/questions/reviews/audits），可入库可忽略 |
| E | 根 | PRD.md/PRD.docx、CLAUDE.md、AGENTS.md |

---

## 2. 分组文件清单

### A. backend/（全部新增）
- **构建/配置**：`pom.xml`（SB 3.2.5 + mybatis-plus 3.5.7 + mysql-connector-j + lombok + **spring-boot-starter-validation**）；`src/main/resources/application.yml`（DB 环境变量占位）；`src/main/resources/db/schema.sql`（5 表 DDL）
- **实体**：`entity/Student.java`、`TimeSlot.java`、`Course.java`、`Lesson.java`（含 `LessonStatus`/`AbsentBy` 枚举）、`Dict.java`
- **持久化**：`mapper/` 5 接口 + `src/main/resources/mapper/*.xml`
- **公共**：`common/Result.java`、`common/BusinessException.java`、`common/GlobalExceptionHandler.java`、`common/vo/*`、`common/dto/*`
- **配置**：`config/MybatisPlusConfig.java`、`config/MyMetaObjectHandler.java`
- **服务**：`service/` Student/TimeSlot/Course/Dict/Lesson/Statistics（+ `impl/`）
- **控制器**：`controller/` Student/TimeSlot/Course/Dict/Lesson/Statistics（覆盖 BE-API-01~33）
- **方法论（仅参考，非运行依赖）**：`ai-knowledge/skill/`（9 个技能）

### B. frontend/（全部新增）
- **脚手架**：`package.json`、`vite.config.ts`（`/api`→`47.116.35.76:8080`）、`tsconfig.json`、`index.html`、`src/main.ts`
- **基础设施**：`src/router/index.ts`、`src/layouts/AdminLayout.vue`、`src/utils/request.ts`、`src/types/index.ts`、`src/constants/status.ts`、`src/store/`
- **接口层**：`src/api/` student / timeSlot / course / dict / lesson / makeUp / statistics（`USE_MOCK=false`）
- **页面**：`src/views/admin/` StudentListView / TimeSlotListView / CourseListView / DictListView / ScheduleView（拖拽排课+html2canvas 出图）；`src/views/dashboard/` TodayView / OverviewView / MonthlyView / MakeUpView

### C. 规格与原型
- `archive-spec/product/overview-be-V1.0.0_I1_备课排课.md`（**BE-API-19 已回写**）
- `archive-spec/product/overview-fe-V1.0.0_I1_备课排课.md`
- `archive-spec/product/ui-V1.0.0_I1_备课排课/_notes.md`
- `prototype/` UI 原型 SFC

### D. 过程痕迹（`.sdd/`，可选入库）
- `state.json`、`tasks-be.md`、`tasks-fe.md`、`checklist-be.md`、`checklist-fe.md`、`questions.md`、`review-waveF.md`、`review-waveF-recheck.md`、`audit-path-alignment.md`、`audit-path-alignment-recheck.md`

### E. 根文档
- `备课排课管理系统PRD.md`、`备课排课管理系统PRD.docx`、`CLAUDE.md`、`AGENTS.md`

---

## 3. Stage 5 / Wave G 关键改动（最新一轮，重点核对）

> 这批是「集成审计发现 10 阻断 → 修复」的改动，是本次收尾的核心增量。

- **LessonController**：新增 BE-API-16 `POST /api/lessons`、17 `DELETE /api/lessons/{id}`、18 `PUT /api/lessons/{id}`、33 `GET /api/lessons/today`；BE-API-19 改为收 `{year,month}` 仅关闭上月 `ABSENT&closed=0` 并返回 `closedCount`；BE-API-23 `/lessons/pending`→`/make-up/pending`；BE-API-25 `/lessons/{id}/close`→`/make-up/{lessonId}/close`
- **StatisticsController**：BE-API-30/31 入参由 `month=YYYY-MM` 改为 `year+month` 数字；BE-API-31 `/history`→`/month/detail`；新增 BE-API-32 `GET /api/statistics/month/income`（取 `course.price`，Q2 口径）
- **pom.xml**：新增 `spring-boot-starter-validation`
- **8 文件 `javax.validation`→`jakarta.validation`**：`CreateLessonDTO`、`UpdateLessonDTO`、`SaveMonthDTO`、`LessonSaveDTO`、`MakeUpDTO`、`AbsentDTO`、`StatusChangeDTO`、`LessonController`
- **overview-be.md**：BE-API-19 说明文字回写（与实现一致）

---

## 4. 未验证 / 待办（非阻塞，提交后继续）

- 后端未 `mvn compile` 运行（沙箱无 JDK）；建议本地编译确认（尤其全仓是否还有 `javax.*` 需迁 `jakarta.*`）
- **Q17** MySQL 库名/账号/密码待填（环境变量），并在 `47.116.35.76` 执行 `db/schema.sql` 建表
- 孤儿端点 `/lessons/{id}/made-up`、`/students/{id}/pause` 待决（补契约或移除）
- 🟡-A 后端冗余 insert 缺 `useGeneratedKeys`；A-17 全局异常待确认；U1-U5 UI 未决

---

## 5. 提交建议命令（仅供参考，你自行调整）

```bash
git init
# 先放 .gitignore 排除 node_modules/dist/target/*.class 等
git add -A
git commit -m "feat: 备课排课管理系统 SDD 全流程产出（前后端+规格）"
```
