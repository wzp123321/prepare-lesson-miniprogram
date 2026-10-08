# Wave F 复评结论（返工后）

- 原 🔴1 路径：已消除
- 原 🔴2 入参：已消除
- 残留 🔴 数：0
- 剩余 🟡 数：3
- 是否需再次返工：否
- 关键确认：最终暴露路径 `GET /api/lessons/student/{studentId}/export`；示例请求 `GET /api/lessons/student/123/export?year=2024&month=9`

## 复评依据（仅评审，未改动任何文件）

### 🔴1 路径 — 已消除（Grep 交叉确认）
- `LessonController` 类级 `@RequestMapping("/api")` + 方法 `@GetMapping("/lessons/student/{studentId}/export")`（LessonController.java:37-38, 75），拼出完整路径精确为：
  `GET /api/lessons/student/{studentId}/export`
  与契约 BE-API-20 逐项一致：base path `/api`、段 `/lessons/student`、末段 `/export`、变量 `{studentId}`。✅
- `StudentController` 不再暴露 `/schedule`：全文 Grep 无 `schedule`/`resolveScheduleMonth`/`YearMonth`/`monthNum`（StudentController.java 仅含 list/detail/create/update/delete/pause）。原 `/{id}/schedule` 映射已删除。✅
- 其他 Controller 无冲突：`CourseController` 的 `/students/{studentId}/course` 属不同端点（课程域），与 `/export` 不冲突。

### 🔴2 入参 — 已消除（Grep 交叉确认）
- 新方法签名 `exportStudentMonthSchedule(@PathVariable Long studentId, @RequestParam int year, @RequestParam int month)`（LessonController.java:76-79）：纯 `year`(int)+`month`(int) 数字签名，与 BE-API-15 风格对齐。
- 全仓 Grep `monthNum`：0 命中。原 `monthNum` 私有参数已删除。✅
- 本方法内无任何 `month=YYYY-MM` 字符串解析、无 `YearMonth.parse` 调用；前端契约请求 `?year=2024&month=9` 直接以 int 绑定，不再触发 `DateTimeParseException` → 500 的回归。✅
- 关于 `YearMonth`：仅 `LessonController` 的 `resolveYearMonthFromBody/Query` 仍 import 并使用（服务于 BE-API-15/19 契约允许的 `date=YYYY-MM` 双模式），**与 BE-API-20 无关**，不构成该端点阻塞。原 🔴2 针对的「/export 端点用 YYYY-MM 解析」已彻底消除。

### 仍满足的合规项（原正向确认均保持）
- 统一 `Result` 包装：`Result.success(studentService.getStudentMonthSchedule(...))`，结构 `{code,message,data}`。✅
- 空数据返 `[]`：`StudentServiceImpl` 查询为 null 置 `new ArrayList<>()`（未改动，签名 `getStudentMonthSchedule(Long,int,int)` 不变）。✅
- 复用 `lessonMapper.selectStudentMonthSchedule(studentId, year, month)`（StudentServiceImpl.java:143），无拼接 SQL；`LessonMapper.xml` 仍用 `#{}` 占位符 + `YEAR()/MONTH()`，参数绑定规范。✅
- DDD 分层：controller 仅路由与参数解析，组装 VO/404 下沉 Service，核心域逻辑未散落。✅
- 无 PNG 生成逻辑（PNG 在前端）、无天溯私有依赖、只读 GET 不受 C-07 写护栏影响。✅

## 剩余 🟡 建议项现状（仅记录，不强制修复）
1. **出参结构形态**：仍为 `StudentMonthScheduleVO{studentInfo, lessons[]}` 扁平列表，足以支撑前端 html2canvas；契约措辞为「日期列/时段列/课程单元映射」，建议与前端确认扁平结构可直接出图（若需 `days/slots/cells` 维度再补）。→ 仍 🟡。
2. **price 口径**：`StudentInfoVO.price` 取自 `student.price`（非 `course.price`），与 B-12 一致，未复现 Wave E 歧义；建议导出文档注明口径。→ 仍 🟡（合规，建议补文档）。
3. **命名统一**：原建议「内部变量统一为 `{studentId}`」，本次返工后 `/export` 端点已采用 `{studentId}`，**已落实，本项关闭**。→ 不再计为 🟡。
4. **A-17 全局异常包装**：本接口 `BusinessException(400/404)` 是否经统一异常处理器包装为 `Result` 仍属待确认项（超出本次返工范围，未在关联文件中验证）。→ 仍 🟡（建议后续 Wave 确认）。

## 结论
两个 🔴 阻塞项（路径不符 / 入参签名导致契约调用 500）均已在返工中消除，残留 🔴 数为 0，关联文件签名与 mapper 调用未被破坏且仍合规。无需再次返工；剩余 3 个 🟡 建议项可在后续 Wave 酌情处理。
