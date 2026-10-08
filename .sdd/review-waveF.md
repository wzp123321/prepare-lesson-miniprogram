# Wave F 交叉评审结论

- 评审对象：BE-API-20 按学生出图数据（F-01）
- 阻塞项(🔴)数：2
- 建议项(🟡)数：4
- 是否需返工：是

## 🔴 阻塞项

1. **HTTP 路径与契约不符（前端联调必断）**
   - 契约（概设 §2.4.4 BE-API-20）：`GET /api/lessons/student/{studentId}/export`
   - 实现：`StudentController` 类级 `@RequestMapping("/api/students")` + 方法 `@GetMapping("/{id}/schedule")`，实际暴露为 `GET /api/students/{id}/schedule`。
   - 偏差：base path（`/api/students` vs `/api/lessons/student`）、末段（`/schedule` vs `/export`）、路径变量名（`{id}` vs `{studentId}`）全部不一致。前端按契约请求会直接 404，html2canvas 取数链路整体断掉。
   - 要求：路径必须精确改为 `GET /api/lessons/student/{studentId}/export`（建议落位到 S04/lesson 相关 Controller，而非 StudentController）。

2. **入参签名与契约不符，契约约定调用会 500**
   - 契约：`?year=&month=`（month 为数字，与同模块 BE-API-15 一致）。
   - 实现：`resolveScheduleMonth` 优先把 `month` 当 `YYYY-MM` 字符串解析（`YearMonth.parse`），否则要求 `year + monthNum`（额外引入未约定的 `monthNum`）。
   - 后果：前端严格按契约传 `?year=2024&month=9` 时，`month="9"` 非空 → `YearMonth.parse("9")` 抛 `DateTimeParseException` → 未捕获即 500（非约定 400）。契约约定的数字 `month` 签名当前完全不可用。
   - 要求：入参统一为 `year`(int) + `month`(int)，与 BE-API-15 风格对齐，删除 `month=YYYY-MM` 私有约定与 `monthNum` 参数。

## 🟡 建议项

1. **出参结构形态**：返回 `StudentMonthScheduleVO{studentInfo, lessons[]}`，每个 cell 含 `date/slotId/slotStart/slotEnd/status/absentBy/reason/makeUpDate`，扁平列表已足够前端 html2canvas 渲染，且不含 PNG 逻辑（符合契约）。契约措辞为"日期列/时段列/课程单元映射"，建议与前端确认该扁平结构可直接出图；若前端期望独立 `days/slots/cells` 维度可再补（非必须）。
2. **price 口径正确（规避 Wave E 问题）**：`StudentInfoVO.price` 取自 `student.price`（非 `course.price`），与 B-12「price 自动取自 student.price」一致，未复现 Wave E 的 `course.price` 歧义。建议保留并导出文档注明口径。
3. **路径变量命名**：即便 base path 修正后，也建议内部命名统一为 `{studentId}` 以契合契约语义（仅命名，不阻断调用）。
4. **全局异常包装**：本接口使用 `BusinessException(400/404)`，需确认 Wave A 的 A-17 全局异常处理器已将其统一包装为 `Result{code,message,data}`，避免 404/400 透出原始栈（见待确认项）。

## 关键发现

- 本波**复现了 Wave E 评审已发现的「路径与概设不符」类问题**：端点被错误挂到 `/api/students/...` 且末段为 `/schedule`，与概设 `/api/lessons/student/{studentId}/export` 偏差，属于同类回归，需作为硬约束重点核查。
- 正向确认项（均合规）：
  - 统一返回体：`Result.success(...)` 包装 `{code,message,data}`，✅。
  - Mapper 复用：正确复用 A-15 的 `lessonMapper.selectStudentMonthSchedule(studentId, year, month)`，无临时拼接 SQL，✅。
  - SQL 注入/参数绑定：`LessonMapper.xml` 用 `#{studentId}/#{year}/#{month}` 占位符 + `YEAR()/MONTH()` 函数，参数绑定规范，无拼接注入风险，✅。
  - DDD 分层：controller 仅做路由与参数解析，组装 VO 与 404 处理下沉到 `StudentServiceImpl`，核心域逻辑未散落 controller，✅。
  - 空数据：查询为 null 时置为 `new ArrayList<>()`，返回 `[]` 而非 null，✅。
  - 无 PNG 生成逻辑（PNG 在前端），✅。
  - 无天溯 `energy-parent` 私有依赖，✅。
  - 只读 GET，不受 C-07 历史月写护栏影响（本接口无写操作），读权限无破坏，✅。

## 待确认项

- A-17 全局异常处理器是否已落地：确认 `BusinessException(400/404)` 经统一异常处理包装为 `Result`，而非透出 5xx 栈（影响 #2 中 parse 异常的最终呈现形态）。
- 端点归属 Controller：是新建/复用 `/api/lessons/...` 相关 Controller，还是仅改 `StudentController` 注解前缀——需与 Wave C/E 的 lesson 接口分组保持一致。
- 出参是否需补充独立 `days/slots/cells` 维度：以前端 html2canvas 实际渲染需求为准（当前扁平 `lessons` 列表已可用）。
