# Wave 5 集成准备 — 前后端路径/契约对齐审计

> 审计者角色：静态集成审计者（fresh context，只读分析，未修改任何代码/配置）
> 审计对象：概设 BE-API-01~33（契约事实源） × 后端 7 个 `*Controller` × 前端 7 个 `api/*.ts`
> baseURL 事实：`frontend/src/utils/request.ts` 中 `axios.create({ baseURL: '/api' })` → 前端请求 URL 已含 `/api` 前缀（mock 仅拦截，URL 仍为真实契约）。
> 结论速读：**33 个 BE-API 中 10 个存在集成阻断（联调必 404/405/400），0 个纯路径契约漂移；阻断全部源于「后端与前端各自偏离契约且方向不同」，孤儿端点/调用较多。**

---

## 1. 对齐矩阵（按 BE-API 编号）

> 判定列：一致=✓ / 阻断=❌ / 仅参数错位=⚠
> 「后端↔前端一致?」指双方实际部署的路径+method 是否相同；「与契约一致?」指是否对齐概设 §2.4 的 BE-API 路径+method。

| BE-API | 契约路径(method) | 后端实际路径(method) | 前端实际调用路径(method) | 后端↔前端一致? | 与契约一致? | 问题简述 |
|---|---|---|---|---|---|---|
| 01 | GET /api/students | GET /api/students | GET /api/students | ✓ | ✓ | — |
| 02 | GET /api/students/{id} | GET /api/students/{id} | GET /api/students/{id} | ✓ | ✓ | — |
| 03 | POST /api/students | POST /api/students | POST /api/students | ✓ | ✓ | — |
| 04 | PUT /api/students/{id} | PUT /api/students/{id} | PUT /api/students/{id} | ✓ | ✓ | — |
| 05 | DELETE /api/students/{id} | DELETE /api/students/{id} | DELETE /api/students/{id} | ✓ | ✓ | — |
| 06 | GET /api/time-slots | GET /api/time-slots | GET /api/time-slots | ✓ | ✓ | — |
| 07 | POST /api/time-slots | POST /api/time-slots | POST /api/time-slots | ✓ | ✓ | — |
| 08 | PUT /api/time-slots/{id} | PUT /api/time-slots/{id} | PUT /api/time-slots/{id} | ✓ | ✓ | — |
| 09 | DELETE /api/time-slots/{id} | DELETE /api/time-slots/{id} | DELETE /api/time-slots/{id} | ✓ | ✓ | — |
| 10 | PUT /api/time-slots/sort | PUT /api/time-slots/sort | PUT /api/time-slots/sort | ✓ | ✓ | — |
| 11 | GET /api/students/{studentId}/course | GET /api/students/{studentId}/course | GET /api/students/{studentId}/course | ✓ | ✓ | — |
| 12 | POST /api/students/{studentId}/course | POST /api/students/{studentId}/course | POST /api/students/{studentId}/course | ✓ | ✓ | — |
| 13 | PUT /api/courses/{id} | PUT /api/courses/{id} | PUT /api/courses/{id} | ✓ | ✓ | — |
| 14 | PUT /api/courses/{id}/disable | PUT /api/courses/{id}/disable | PUT /api/courses/{id}/disable | ✓ | ✓ | — |
| 15 | GET /api/lessons/month | GET /api/lessons/month | GET /api/lessons/month | ✓ | ✓ | 入参 year+month 三方一致 |
| 16 | POST /api/lessons | **无** | POST /api/lessons | ❌ | ❌ | **后端缺失**：拖拽建课无端点 |
| 17 | DELETE /api/lessons/{id} | **无** | DELETE /api/lessons/{id} | ❌ | ❌ | **后端缺失**：删课无端点 |
| 18 | PUT /api/lessons/{id} | **无** | PUT /api/lessons/{id} | ❌ | ❌ | **后端缺失**：改课无端点 |
| 19 | POST /api/lessons/save-month | POST /api/lessons/save-month | POST /api/lessons/save-month | ⚠ | ⚠ | 路径同，但 body/param 错位（见 §5） |
| 20 | GET /api/lessons/student/{studentId}/export | GET /api/lessons/student/{studentId}/export | GET /api/lessons/student/{studentId}/export | ✓ | ✓ | 入参 year+month 一致 |
| 21 | PUT /api/lessons/{id}/absent | PUT /api/lessons/{id}/absent | PUT /api/lessons/{id}/absent | ✓ | ✓ | — |
| 22 | PUT /api/lessons/{id}/status | PUT /api/lessons/{id}/status | PUT /api/lessons/{id}/status | ✓ | ✓ | — |
| 23 | GET /api/make-up/pending | GET /api/lessons/pending | GET /api/make-up/pending | ❌ | ❌ | **路径错位**：/make-up vs /lessons |
| 24 | PUT /api/lessons/{id}/make-up | PUT /api/lessons/{id}/make-up | PUT /api/lessons/{id}/make-up | ✓ | ✓ | — |
| 25 | PUT /api/make-up/{lessonId}/close | PUT /api/lessons/{id}/close | PUT /api/make-up/{lessonId}/close | ❌ | ❌ | **路径错位**：/make-up vs /lessons |
| 26 | GET /api/dicts | GET /api/dicts | GET /api/dicts | ✓ | ✓ | — |
| 27 | POST /api/dicts | POST /api/dicts | POST /api/dicts | ✓ | ✓ | — |
| 28 | PUT /api/dicts/{id} | PUT /api/dicts/{id} | PUT /api/dicts/{id} | ✓ | ✓ | — |
| 29 | DELETE /api/dicts/{id} | DELETE /api/dicts/{id} | DELETE /api/dicts/{id} | ✓ | ✓ | — |
| 30 | GET /api/statistics/month | GET /api/statistics/month | GET /api/statistics/month | ⚠ | ⚠ | 入参错位：year+month vs month=YYYY-MM |
| 31 | GET /api/statistics/month/detail | GET /api/statistics/history | GET /api/statistics/month/detail | ❌ | ❌ | **路径错位**+入参错位 |
| 32 | GET /api/statistics/month/income | **无** | GET /api/statistics/month/income | ❌ | ❌ | **后端缺失**：月收入端点未实现 |
| 33 | GET /api/lessons/today | **无** | GET /api/lessons/today | ❌ | ❌ | **后端缺失**：今日视图端点未实现 |

---

## 2. 集成阻断清单（联调必 404/405/400）

> 共 **10** 项。每一项都意味着前端在 `USE_MOCK=false` 后必然失败。

1. **BE-API-16 拖拽建课** — 前端 `POST /api/lessons`，后端**无此端点**（LessonController 仅实现 save-month/getMonth/export/顺延补课，未实现单条建/改/删）。→ 404。
2. **BE-API-17 删课** — 前端 `DELETE /api/lessons/{id}`，后端无。→ 404。
3. **BE-API-18 改课** — 前端 `PUT /api/lessons/{id}`，后端无。→ 404。
4. **BE-API-19 保存当月** — 路径/方法一致，但**请求体错位**（详见 §5）。前端只发 `{year,month}`，后端要 `month` query(YYYY-MM) + `List<LessonSaveDTO>` body；双方都未发送契约要求的「网格变更集」。→ 400 或丢失排课数据。
5. **BE-API-23 待补列表** — 路径错位：前端 `GET /api/make-up/pending`，后端为 `GET /api/lessons/pending`。→ 404。
6. **BE-API-25 手动关闭** — 路径错位：前端 `PUT /api/make-up/{lessonId}/close`，后端为 `PUT /api/lessons/{id}/close`。→ 404。
7. **BE-API-30 月总览** — 入参错位：前端发 `year`(num)+`month`(num)，后端只认单一 `month`=YYYY-MM 字符串。→ 400（后端 `YearMonth.parse` 解析 year 值失败）。
8. **BE-API-31 历史明细** — 路径错位+入参错位：前端 `GET /api/statistics/month/detail?year=&month=`，后端 `GET /api/statistics/history?month=YYYY-MM`。→ 404 + 400。
9. **BE-API-32 月收入** — 后端**完全缺失** `/api/statistics/month/income`。→ 404。
10. **BE-API-33 今日视图** — 后端**完全缺失** `/api/lessons/today`。→ 404。

---

## 3. 契约漂移清单

> 共 **0** 项纯路径漂移。

说明：本系统的偏离**全部表现为集成阻断**，而非「后端↔前端已达成一致却偏离契约」的漂移。原因是阻断项的后端实现与前端调用在**路径上彼此也不同**（如 23/25/31 后端自创 `/lessons/...` 而前端按契约走 `/make-up/...`；16/17/18/32/33 后端整段未实现）。

> 唯一接近「漂移」语义的是 **BE-API-19 的请求体形态**：后端与前端在「都不发网格变更集」上一点头一致，但二者 body 结构仍不同（FE `{year,month}` vs BE `List<LessonSaveDTO>`+`month` query），且都偏离契约「网格变更集」定义——归为 §2 第 4 项参数阻断，不计入纯路径漂移。

**决策建议**（针对可能的潜在漂移面）：若团队决定「以某一方实现为准回拉契约」，优先统一 statistics 的月份入参约定（`year`+`month` 数字 vs `month=YYYY-MM` 字符串，见 §5），再反向修订概设 §2.4.8。

---

## 4. 孤儿端点

### 4.1 后端有、前端无调用的端点（4）
| 后端端点 | method | 性质 | 备注 |
|---|---|---|---|
| `/api/lessons/pending` | GET | BE-API-23 的后端变体 | 前端走 `/make-up/pending`，此端点永不被调用 |
| `/api/lessons/{id}/close` | PUT | BE-API-25 的后端变体 | 前端走 `/make-up/{lessonId}/close`，永不被调用 |
| `/api/lessons/{id}/made-up` | PUT | 额外端点（D-04 标记已补） | 概设无对应 BE-API；前端用 `/make-up`(BE-API-24) 经 makeUpDate 改状态，不调此端点 |
| `/api/students/{id}/pause` | PUT | 额外端点（暂停归档） | 概设无对应 BE-API；前端用 BE-API-04 部分字段改 `status`，不调此端点 |

> 以上 `made-up`/`close`/`pause` 是后端相较概设的**能力外延**，无契约编号、无前端消费，联调无影响但属「未对齐概设」的待决项（建议要么补契约编号，要么移除）。

### 4.2 前端调用、后端没有的端点（8）
即 §2 阻断清单中「后端整段缺失」或「路径错配」导致前端调用落空者：
- `POST /api/lessons`（BE-API-16）
- `DELETE /api/lessons/{id}`（BE-API-17）
- `PUT /api/lessons/{id}`（BE-API-18）
- `GET /api/make-up/pending`（BE-API-23，后端在 `/lessons/pending`）
- `PUT /api/make-up/{lessonId}/close`（BE-API-25，后端在 `/lessons/{id}/close`）
- `GET /api/statistics/month/detail`（BE-API-31，后端在 `/statistics/history`）
- `GET /api/statistics/month/income`（BE-API-32，后端无）
- `GET /api/lessons/today`（BE-API-33，后端无）

---

## 5. 入参差异（针对阻断/漂移项）

| BE-API | 维度 | 契约要求 | 后端实际 | 前端实际 | 三方是否一致 |
|---|---|---|---|---|---|
| 16/17/18 | path var | `{id}`(Long) | 无端点 | `{id}`(Long) | 后端缺失，无法对齐 |
| 19 | body | 网格变更集（增/改/删 lesson） | `@RequestParam(required=false) String month`(YYYY-MM) + `@RequestBody List<LessonSaveDTO>` | `@RequestBody {year:number, month:number}` | ❌ 三者均不同：前端未传网格、后端要 list+query、契约要变更集 |
| 19 | month 格式 | 隐含 year+month | `month`=YYYY-MM 字符串（可省） | `year`+`month` 数字 | ❌ |
| 23 | path | `/api/make-up/pending`（无 path var） | `/api/lessons/pending`（无 path var） | `/api/make-up/pending` | ❌ 仅路径前缀不同，无 path var/query 差异 |
| 25 | path var | `lessonId`(Long) | `id`(Long) @ `/lessons/{id}/close` | `lessonId`(Long) @ `/make-up/{lessonId}/close` | ❌ 路径段不同（语义同，均 Long） |
| 30 | query | `year`(int)+`month`(int) | `month`(String, YYYY-MM，必填) | `year`(int)+`month`(int) | ❌ 后端不认 year；字符串 vs 数字 |
| 31 | path | `/api/statistics/month/detail` | `/api/statistics/history` | `/api/statistics/month/detail` | ❌ 路径段不同 |
| 31 | query | `year`(int)+`month`(int) | `month`(String, YYYY-MM，必填) | `year`(int)+`month`(int) | ❌ |
| 32 | query | `year`(int)+`month`(int) | 无端点 | `year`(int)+`month`(int) | 后端缺失 |
| 33 | query | 无 | 无端点 | 无 | 后端缺失 |

**关键潜在坑（后端内部不一致，需决策统一）**：`LessonController` 的 BE-API-15/19/20 用 **`year`+`month` 数字**入参；而 `StatisticsController` 的 BE-API-30/31 用 **`month=YYYY-MM` 单字符串**入参。前端统一按数字 `year+month` 发，因此 statistics 三个接口（30/31/32）全部对不齐——联调前必须二选一统一月份入参约定，否则 30/31 必 400、32 必 404。

---

## 6. 下一步建议（给集成负责人）

1. **优先修复 10 个阻断点**，按「改动量小、影响大」排序：
   - 路径对齐（纯重命名，最低风险）：BE-API-23 改后端 `/lessons/pending`→`/make-up/pending`（或前端反向）；BE-API-25 改后端 `/lessons/{id}/close`→`/make-up/{lessonId}/close`；BE-API-31 改后端 `/statistics/history`→`/statistics/month/detail`。
   - 补齐缺失端点：BE-API-16/17/18（LessonController 单条 CRUD）、BE-API-32（statistics/month/income）、BE-API-33（lessons/today）。
   - 统一月份入参：选定 `year`+`month` 数字 或 `month=YYYY-MM` 其一，全局（尤其 statistics）对齐；推荐沿用契约的数字约定，改 StatisticsController。
   - 重定义 BE-API-19 请求体：明确「前端究竟发什么」——要么发 `{year,month}`+网格变更集 list，要么重构后端接收 `{year,month}` 并在 body 内含变更集；当前前端只发 year/month 会丢失排课数据。
2. **处理 4 个后端孤儿端点**：`lessons/pending`、`lessons/{id}/close`、`lessons/{id}/made-up`、`students/{id}/pause` —— 决定补契约编号并入前端，或与前端对齐后保留、写进概设附录；避免「实现存在但无契约」的灰色地带。
3. **回拉或确认契约**：因阻断项双方均偏离概设，建议 Wave 5 收尾时由后端/前端各出一人对照本表逐条拍板「以哪方为准」，再统一修订 `overview-be` §2.4（尤其 statistics 入参与 make-up 路径），使三方最终收敛。
4. **联调前开关**：各 `api/*.ts` 的 `USE_MOCK` 当前全为 `true`，须待上述阻断清零后逐文件置 `false`，避免 mock 掩盖真实 404。

---

### 审计覆盖统计
- 契约 BE-API 覆盖：**33** 个（01~33 全覆盖）
- 后端 Controller：**7** 个（Student/TimeSlot/Course/Dict/Lesson/Statistics + package-info 忽略）
- 前端 api 模块：**7** 个（student/timeSlot/course/dict/lesson/makeUp/statistics）
- 集成阻断点：**10** 个（16/17/18/19/23/25/30/31/32/33）
- 纯路径契约漂移：**0** 个
- 后端孤儿端点：**4** 个；前端孤儿调用（落空）：**8** 个（与阻断项重合计数）
