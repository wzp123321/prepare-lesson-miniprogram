# Wave 5 集成准备 — 路径/契约对齐复审计（Recheck）

> 复审计者角色：静态集成复审计者（fresh context，只读分析，未修改任何代码/配置）
> 复审计对象：上一轮 10 个集成阻断（BE-API 16/17/18/19/23/25/30/31/32/33）× 最新后端（LessonController 改于 Oct 8 09:00、StatisticsController 改于 Oct 8 08:58）× 最新前端 `api/*.ts`
> baseURL 事实不变：`frontend/src/utils/request.ts` 中 `axios.create({ baseURL: '/api' })`，前端请求 URL 已含 `/api` 前缀（mock 仅拦截，URL 仍为真实契约，忽略 USE_MOCK 开关）。
> 结论速读：**上一轮 10 个集成阻断已全部清零（残留 0）；未发现任何新阻断。仅余 1 处契约正文偏差（BE-API-19 body 形态），属"已对齐实现、未对齐契约文字"的待回拉项，非联调阻断。**

---

## 1. 10 阻断逐项复核表

| BE-API | 原问题 | 后端现状（路径/method） | 前端现状（路径/method） | 是否已消除 |
|---|---|---|---|---|
| 16 | 拖拽建课后端缺失 → 404 | `POST /api/lessons`（`@PostMapping("/lessons")`，CreateLessonDTO） | `POST /api/lessons`（lesson.ts:96） | ✓ 已消除 |
| 17 | 删课后端缺失 → 404 | `DELETE /api/lessons/{id}`（`@DeleteMapping("/lessons/{id}")`） | `DELETE /api/lessons/{id}`（lesson.ts:144） | ✓ 已消除 |
| 18 | 改课后端缺失 → 404 | `PUT /api/lessons/{id}`（`@PutMapping("/lessons/{id}")`，UpdateLessonDTO） | `PUT /api/lessons/{id}`（lesson.ts:157） | ✓ 已消除 |
| 19 | 路径同但 body 错位 → 400 | `POST /api/lessons/save-month`（`@RequestBody SaveMonthDTO{year,month}`） | `POST /api/lessons/save-month`（body `{year,month}`，lesson.ts:194） | ✓ 已消除（body 现已对齐，详见 §3 偏差说明） |
| 23 | 路径错位 /make-up vs /lessons → 404 | `GET /api/make-up/pending`（`@GetMapping("/make-up/pending")`，已改名） | `GET /api/make-up/pending`（makeUp.ts:19） | ✓ 已消除 |
| 25 | 路径错位 /make-up vs /lessons → 404 | `PUT /api/make-up/{lessonId}/close`（`@PutMapping("/make-up/{lessonId}/close")`，已改名，pathvar=lessonId） | `PUT /api/make-up/{lessonId}/close`（makeUp.ts:33） | ✓ 已消除 |
| 30 | 入参错位 year+month vs YYYY-MM → 400 | `GET /api/statistics/month`（`@RequestParam int year, int month`，已改数字入参） | `GET /api/statistics/month`（params {year,month}，statistics.ts:11） | ✓ 已消除 |
| 31 | 路径+入参错位 /history vs /month/detail → 404+400 | `GET /api/statistics/month/detail`（`@GetMapping("/month/detail")`，已改名，year+month 入参） | `GET /api/statistics/month/detail`（statistics.ts:35） | ✓ 已消除 |
| 32 | 月收入后端缺失 → 404 | `GET /api/statistics/month/income`（`@GetMapping("/month/income")`，已补齐） | `GET /api/statistics/month/income`（statistics.ts:48） | ✓ 已消除 |
| 33 | 今日视图后端缺失 → 404 | `GET /api/lessons/today`（`@GetMapping("/lessons/today")`，已补齐） | `GET /api/lessons/today`（lesson.ts:294） | ✓ 已消除 |

**10/10 全部消除。**

---

## 2. 残留阻断总数

> **0** 个集成阻断（目标 0，已达成）。
> 即：`USE_MOCK=false` 后，原 10 处必 404/405/400 的端点现已全部可正常路由与解析。

---

## 3. 新发现阻断（原 10 之外）

> **无新阻断。** 本轮复审计对全部 33 个 BE-API 重新做一次端到端比对（后端 7 个 Controller × 前端 7 个 api 模块），除已消除的 10 项外，未引入任何新的路径/方法/入参错位。

### 3.1 残留契约偏差（非阻断，建议回拉概设）

仅 1 处：

- **BE-API-19 请求体形态**：契约 §2.4.4 定义为「当月网格变更集（增/改/删 lesson）」，但后端按团队决策改版为 `@RequestBody SaveMonthDTO{year,month}`（仅做"关闭上月顺延 + 返回 closedCount"），前端也仅发 `{year,month}`。
  - **性质**：后端 ↔ 前端 **已对齐**（不再 400），但**偏离契约文字**（网格变更集未实现/未传输，假定由 BE-API-16/17/18 逐条落库替代）。
  - **影响**：联调不阻断，但属于"实现与概设不一致"的灰色地带，需在 Wave 5 收尾时把概设 §2.4.4 BE-API-19 正文改为"接收 {year,month}，仅关上月顺延"以三方收敛。
  - **动作**：建议由集成负责人确认该决策，回写 `overview-be` §2.4.4；**不是 bug，不需改代码**。

### 3.2 后端孤儿端点（同上一轮，未新增，非阻断）

- `PUT /api/lessons/{id}/made-up`（D-04，后端有、无契约编号、前端不调用）
- `PUT /api/students/{id}/pause`（后端有、无契约编号、前端不调用）
- 上一轮的另外两个孤儿 `GET /api/lessons/pending`、`PUT /api/lessons/{id}/close` 已在 Wave G1/G2 中**改名为契约路径**（`/make-up/pending`、`/make-up/{lessonId}/close`），不再是孤儿。
- 提示：`made-up`/`pause` 为后端能力外延，建议补契约编号或移除，但**不影响联调**。

---

## 4. 给集成负责人的结论

- **结论：10 个集成阻断已全部清零，残留阻断数 = 0，无新阻断。** Wave G1（LessonController 单条 CRUD + today）+ Wave G2（StatisticsController 三统计接口改名/改参/补齐）的修复与本轮审计结果一致。
- **可推进事项**：
  1. 原 10 处 `api/*.ts` 的 `USE_MOCK` 当前仍为 `true`，阻断清零后已可将 lesson.ts / makeUp.ts / statistics.ts 的开关逐文件置 `false` 进行真实联调（建议按模块灰度开，而非一次性全开）。
  2. **唯一需回拉项**：BE-API-19 契约正文与实际实现（{year,month} 版）不一致，请确认决策并修订 `overview-be` §2.4.4，使三方文字收敛（非代码改动）。
  3. 两个遗留孤儿端点 `made-up`/`pause` 建议补契约或移除，非阻断、可后续处理。
- **备注**：本轮复审计为只读静态分析，未改动任何代码/配置。

---

### 复审计覆盖统计
- 契约 BE-API 覆盖：33 个（01~33 全覆盖，二次比对）
- 后端 Controller：7 个（重点复核 Lesson/Statistics，余 5 个时间戳为 Sep 30 未变）
- 前端 api 模块：7 个（lesson/makeUp/statistics 真实 URL 复核）
- 集成阻断点（复审计）：**0**（原 10 项全部 ✓ 消除）
- 新阻断：**0**
- 残留契约文字偏差（非阻断）：1（BE-API-19 body 形态）
- 后端孤儿端点：2（made-up / pause，同上一轮，非阻断）
