package com.lesson.schedule.service.impl;

import com.lesson.schedule.ai.AiScheduleTools;
import com.lesson.schedule.common.AiConstants;
import com.lesson.schedule.common.BusinessException;
import com.lesson.schedule.common.dto.AiActionDTO;
import com.lesson.schedule.common.dto.AiHistoryItem;
import com.lesson.schedule.common.dto.AiApplyDTO;
import com.lesson.schedule.common.dto.AiChatDTO;
import com.lesson.schedule.common.dto.AiSelectionDTO;
import com.lesson.schedule.common.dto.BatchLessonDTO;
import com.lesson.schedule.common.vo.AiApplyResultVO;
import com.lesson.schedule.common.vo.AiChatVO;
import com.lesson.schedule.common.vo.BatchLessonResultVO;
import com.lesson.schedule.common.vo.LessonCellVO;
import com.lesson.schedule.common.vo.MonthGridVO;
import com.lesson.schedule.entity.Student;
import com.lesson.schedule.entity.TimeSlot;
import com.lesson.schedule.service.AiScheduleService;
import com.lesson.schedule.service.LessonService;
import com.lesson.schedule.service.StudentService;
import com.lesson.schedule.service.TimeSlotService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 智能排课实现（Spring AI → DeepSeek）。
 * <p>思路：把「学生 / 时段 / 该月已有排课」作为上下文喂给模型，要求它只输出结构化方案；
 * 方案不落库，等老师在前端确认后走 {@link #apply} 执行。这样模型说错话也不会污染课表。</p>
 */
@Service
public class AiScheduleServiceImpl implements AiScheduleService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    /** 系统提示词：说明可用工具、约束只用给定数据、写操作只出方案 */
    private static final String SYSTEM_PROMPT = """
            你是「备课排课管理系统」的排课助手：既能回答课表相关问题，也能把老师的指令翻译成待确认的排课方案。

            【查询类问题】必须先调用工具拿准确数据再回答，禁止自己心算：
              - unscheduledStudents / freeSlots / studentLessons：课表查询
              - monthStatistic / monthIncome：统计与收入
              - pendingMakeUps：待补课
              - scheduleHealth：体检与改进建议
            查完直接用 reply 说明结论，actions 返回空数组。
            若结论里点名了具体学生 / 课次，可在 reply 末尾补一句「需要的话可以直接说：给张三排课」这类下一步提示。

            【写操作】只输出方案，不要假设已执行。type 取值：
              - CREATE 建课 / DELETE 删课 / UPDATE 调课（换时段或日期）
              - ABSENT 标记顺延（必须给 absentBy=student|teacher 与 absentReason）
              - MAKEUP_ARRANGE 安排补课（给 lessonId 与 makeUpDate）
              - MAKEUP_DONE 标记已补（给 lessonId）
              - BATCH_CREATE 批量循环（给 studentId、slotId、weekdays(1..7)、startDate、endDate）
              - COPY_WEEK 复制课表（给 sourceFrom、targetFrom，均为该周周一日期）

            【参数不全时用 ask 反问（重要）】
            要生成写操作但缺必填参数时，**不要编造、也不要只在 reply 里写一句追问**，改用 ask 让老师点选：
              {"field":"studentId","label":"这一节排给哪位学生？",
               "options":[{"value":"1","label":"张三 · 五年级","hint":"本月已排 3 节"}]}
            - 一次只问一个参数，优先级：学生 → 日期 → 时段 → 其余（请假方 / 补课日期等）。
            - field 与 value 口径：
                studentId  → value 取学生 id（来自【学生列表】），label 用「姓名 · 年级」
                lessonDate → value 用 yyyy-MM-dd，label 同；给最近 7 天 + 目标月内可用日期，最多 8 个
                slotId     → value 取时段 id（来自【时段列表】），label 用「09:00-10:30」
                lessonId   → value 取课次 id（来自【该月已有排课】），label 用「10-10 周六 09:00-10:30 张三」
                absentBy   → value 用 student|teacher，label 用 学生请假|老师请假
                makeUpDate → value 用 yyyy-MM-dd，label 同
            - options 必须真实取自上下文，严禁编造；没有合适候选时宁可不问该项。
            - ask 与 actions 互斥：出了 ask 就把 actions 留空；参数齐了才输出 actions，且不要再多追问。
            - 若【老师刚选择】里已有该 field 的值，直接采用，不要重复问同一个参数。
            - 老师一次说全（学生 + 日期 + 时段都有）时，直接给 actions，不要问。

            【查询结论可直接接着办时，也给出 ask】
            - 查询类问题若结论天然指向一个后续动作（例：老师说「本月还有谁没排课」，
              接下来通常就要给这些人排课），在 reply 说明结论之后，**顺带给出 ask**：
                field=studentId，options 取刚点名的这几位学生（value 用其 id，label 用「姓名 · 年级」）。
              这样老师点一下就能直接进入排课，不用再打一遍名字。
            - 仅当结论确实指向一个可直接执行的后续动作时才给；纯统计、纯了解型的问题（如「本月收入多少」）不要给 ask。

            【通用规则】
            1. 只能用【学生列表】【时段列表】【该月已有排课】里出现过的 id 和取值，严禁编造。
            2. lessonDate / startDate / endDate / makeUpDate 格式 yyyy-MM-dd；建课类日期必须落在【目标月份】内。
            3. CREATE / UPDATE / BATCH_CREATE 前先查【该月已有排课】：目标「日期+时段」已有人时，
               把 blocked 填成「该时段已排 XXX」，不要当作可执行动作。
            4. CREATE / UPDATE 必须给全 studentId、studentName、lessonDate、slotId、slotLabel；
               DELETE / UPDATE / ABSENT / MAKEUP_* 涉及已有课次时必须给 lessonId。
            5. 老师说话口语化时（如「周六上午」）自行对应到最接近的时段，并在 note 里写明对应关系。
            6. note 用一句中文写清这个动作要干什么。
            7. 信息不足时用 ask 反问（见上），actions 留空；不要只在 reply 里写一句追问就完事。
            8. 只输出 JSON，字段为 reply（字符串）、actions（数组）、ask（对象或 null），不要输出多余内容。
            """;

    private final ChatClient chatClient;
    private final LessonService lessonService;
    private final StudentService studentService;
    private final TimeSlotService timeSlotService;
    private final AiScheduleTools aiScheduleTools;

    /** DeepSeek API Key：来自 spring.ai.openai.api-key（可直接写配置，也可由环境变量覆盖） */
    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    public AiScheduleServiceImpl(ChatModel chatModel,
                                 LessonService lessonService,
                                 StudentService studentService,
                                 TimeSlotService timeSlotService,
                                 AiScheduleTools aiScheduleTools) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.lessonService = lessonService;
        this.studentService = studentService;
        this.timeSlotService = timeSlotService;
        this.aiScheduleTools = aiScheduleTools;
    }

    @Override
    public AiChatVO chat(AiChatDTO dto) {
        if (AiConstants.isApiKeyMissing(apiKey)) {
            throw new BusinessException(500,
                    "未配置 DeepSeek API Key：请在 application.yml 的 spring.ai.openai.api-key 填写，"
                            + "或设置环境变量 AI_DEEPSEEK_API_KEY 后重启后端");
        }
        if (dto == null || dto.getMessage() == null || dto.getMessage().isBlank()) {
            throw new BusinessException(400, "请输入排课指令");
        }
        YearMonth ym = (dto.getYear() == null || dto.getMonth() == null)
                ? YearMonth.now()
                : YearMonth.of(dto.getYear(), dto.getMonth());

        String context = buildContext(ym);
        String history = buildHistory(dto.getHistory());
        String selection = buildSelection(dto.getSelection());
        AiChatVO plan;
        try {
            plan = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(history + context + selection + "\n【老师指令】" + dto.getMessage())
                    .tools(aiScheduleTools)
                    .call()
                    .entity(AiChatVO.class);
        } catch (Exception e) {
            throw new BusinessException(500, "AI 调用失败：" + e.getMessage());
        }
        if (plan == null) {
            throw new BusinessException(500, "AI 未返回可用方案，请重试或换个说法");
        }
        if (plan.getActions() == null) {
            plan.setActions(new ArrayList<>());
        }
        // ask 与 actions 互斥：模型若两个都给了，以 ask 为准（先把参数问清楚再执行）
        if (plan.getAsk() != null) {
            plan.setActions(new ArrayList<>());
        }
        return plan;
    }

    @Override
    public AiApplyResultVO apply(AiApplyDTO dto) {
        List<AiActionDTO> actions = dto == null ? null : dto.getActions();
        if (actions == null || actions.isEmpty()) {
            throw new BusinessException(400, "没有可执行的排课动作");
        }
        AiApplyResultVO result = new AiApplyResultVO();
        List<String> messages = new ArrayList<>();
        int created = 0;
        int deleted = 0;
        int updated = 0;
        int failed = 0;

        for (AiActionDTO a : actions) {
            if (a == null || a.getType() == null || a.getType().isBlank()) {
                failed++;
                messages.add("跳过一个信息不完整的动作");
                continue;
            }
            if (a.getBlocked() != null && !a.getBlocked().isBlank()) {
                failed++;
                messages.add(describe(a) + " 未执行：" + a.getBlocked());
                continue;
            }
            try {
                switch (a.getType().toUpperCase()) {
                    case "CREATE" -> {
                        requireCreateFields(a);
                        lessonService.createLesson(a.getStudentId(), a.getSlotId(),
                                LocalDate.parse(a.getLessonDate()));
                        created++;
                    }
                    case "DELETE" -> {
                        requireLessonId(a);
                        lessonService.deleteLesson(a.getLessonId());
                        deleted++;
                    }
                    case "UPDATE" -> {
                        requireLessonId(a);
                        requireCreateFields(a);
                        lessonService.updateLesson(a.getLessonId(), a.getSlotId(),
                                LocalDate.parse(a.getLessonDate()));
                        updated++;
                    }
                    case "ABSENT" -> {
                        requireLessonId(a);
                        if (a.getAbsentBy() == null || a.getAbsentBy().isBlank()) {
                            throw new BusinessException(400, "缺少请假方 absentBy");
                        }
                        if (a.getAbsentReason() == null || a.getAbsentReason().isBlank()) {
                            throw new BusinessException(400, "缺少顺延原因 absentReason");
                        }
                        lessonService.markAbsent(a.getLessonId(), a.getAbsentBy(), a.getAbsentReason());
                        updated++;
                    }
                    case "MAKEUP_ARRANGE" -> {
                        requireLessonId(a);
                        if (a.getMakeUpDate() == null || a.getMakeUpDate().isBlank()) {
                            throw new BusinessException(400, "缺少补课日期 makeUpDate");
                        }
                        lessonService.arrangeMakeUp(a.getLessonId(), LocalDate.parse(a.getMakeUpDate()));
                        updated++;
                    }
                    case "MAKEUP_DONE" -> {
                        requireLessonId(a);
                        lessonService.markMadeUp(a.getLessonId());
                        updated++;
                    }
                    case "BATCH_CREATE" -> {
                        if (a.getStudentId() == null || a.getSlotId() == null
                                || a.getWeekdays() == null || a.getWeekdays().isEmpty()
                                || a.getStartDate() == null || a.getEndDate() == null) {
                            throw new BusinessException(400, "批量排课缺少学生 / 时段 / 星期 / 日期区间");
                        }
                        BatchLessonDTO batch = new BatchLessonDTO();
                        batch.setStudentId(a.getStudentId());
                        batch.setSlotId(a.getSlotId());
                        batch.setWeekdays(a.getWeekdays());
                        batch.setStartDate(LocalDate.parse(a.getStartDate()));
                        batch.setEndDate(LocalDate.parse(a.getEndDate()));
                        BatchLessonResultVO br = lessonService.batchCreateLessons(batch);
                        created += br.getCreated();
                        if (br.getSkipped() > 0) {
                            messages.add("批量排课跳过 " + br.getSkipped() + " 节"
                                    + (br.getSkippedDates().isEmpty() ? ""
                                    : "：" + String.join("、", br.getSkippedDates())));
                        }
                    }
                    case "COPY_WEEK" -> {
                        if (a.getSourceFrom() == null || a.getTargetFrom() == null) {
                            throw new BusinessException(400, "复制课表缺少 sourceFrom / targetFrom");
                        }
                        BatchLessonResultVO cr = lessonService.copyWeek(
                                LocalDate.parse(a.getSourceFrom()), LocalDate.parse(a.getTargetFrom()));
                        created += cr.getCreated();
                        if (cr.getSkipped() > 0) {
                            messages.add("复制课表跳过 " + cr.getSkipped() + " 节"
                                    + (cr.getSkippedDates().isEmpty() ? ""
                                    : "：" + String.join("、", cr.getSkippedDates())));
                        }
                    }
                    default -> {
                        failed++;
                        messages.add(describe(a) + " 未执行：未知动作类型 " + a.getType());
                    }
                }
            } catch (Exception e) {
                failed++;
                messages.add(describe(a) + " 执行失败：" + e.getMessage());
            }
        }

        result.setCreated(created);
        result.setDeleted(deleted);
        result.setUpdated(updated);
        result.setFailed(failed);
        result.setMessages(messages);
        return result;
    }

    private void requireCreateFields(AiActionDTO a) {
        if (a.getStudentId() == null || a.getSlotId() == null
                || a.getLessonDate() == null || a.getLessonDate().isBlank()) {
            throw new BusinessException(400, "缺少学生 / 时段 / 日期");
        }
    }

    private void requireLessonId(AiActionDTO a) {
        if (a.getLessonId() == null) {
            throw new BusinessException(400, "缺少课次 id");
        }
    }

    /** 把前端回传的最近几轮对话拼进 prompt，支持「改成周日上午」这类追问 */
    private String buildHistory(List<AiHistoryItem> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("【最近对话】\n");
        int from = Math.max(0, history.size() - 8);
        for (AiHistoryItem h : history.subList(from, history.size())) {
            if (h == null || h.getText() == null || h.getText().isBlank()) {
                continue;
            }
            sb.append("user".equalsIgnoreCase(h.getRole()) ? "老师：" : "助手：")
                    .append(h.getText()).append('\n');
        }
        sb.append('\n');
        return sb.toString();
    }

    /** 把老师对上一轮 ask 的点选结果拼进 prompt（带结构化 id，避免模型把显示文本认成姓名） */
    private String buildSelection(AiSelectionDTO sel) {
        if (sel == null || sel.getField() == null || sel.getField().isBlank()) {
            return "";
        }
        return "【老师刚选择】" + sel.getField() + " = " + sel.getValue()
                + "（显示为「" + blankToDash(sel.getLabel()) + "」）\n";
    }

    /** 组装模型上下文：今天、目标月、学生、时段、该月已有排课 */
    private String buildContext(YearMonth ym) {
        StringBuilder sb = new StringBuilder();
        LocalDate today = LocalDate.now();
        sb.append("【今天】").append(today).append(' ').append(weekdayCN(today)).append('\n');
        sb.append("【目标月份】").append(ym.getYear()).append('-')
                .append(String.format("%02d", ym.getMonthValue())).append('\n');

        sb.append("【学生列表】（id | 姓名 | 年级 | 状态）\n");
        List<Student> students = studentService
                .listStudents(null, null, null, 1, 200).getList();
        for (Student s : students) {
            sb.append("  ").append(s.getId()).append(" | ").append(s.getName())
                    .append(" | ").append(blankToDash(s.getGrade()))
                    .append(" | ").append(s.getStatus() != null && s.getStatus() == 1 ? "在读" : "暂停")
                    .append('\n');
        }

        List<TimeSlot> slots = timeSlotService.listAll();
        sb.append("【时段列表】（id | 起止）\n");
        for (TimeSlot t : slots) {
            sb.append("  ").append(t.getId()).append(" | ").append(slotLabel(t)).append('\n');
        }

        sb.append("【该月已有排课】（lessonId | 日期 星期 | 时段 | 学生 | 状态）\n");
        MonthGridVO grid = lessonService.getMonthGrid(ym.getYear(), ym.getMonthValue());
        int count = 0;
        if (grid.getCells() != null) {
            for (LessonCellVO c : grid.getCells().values()) {
                sb.append("  ").append(c.getId()).append(" | ")
                        .append(c.getLessonDate()).append(' ').append(weekdayCN(c.getLessonDate()))
                        .append(" | ").append(slotLabelById(slots, c.getSlotId()))
                        .append(" | ").append(c.getStudentName())
                        .append(" | ").append(statusLabel(c.getStatus()))
                        .append('\n');
                count++;
            }
        }
        if (count == 0) {
            sb.append("  （本月暂无排课）\n");
        }
        return sb.toString();
    }

    private String slotLabelById(List<TimeSlot> slots, Long slotId) {
        if (slotId == null) {
            return "未知时段";
        }
        for (TimeSlot t : slots) {
            if (slotId.equals(t.getId())) {
                return slotLabel(t);
            }
        }
        return "时段" + slotId;
    }

    private String slotLabel(TimeSlot slot) {
        String start = slot.getStartTime() == null ? "" : HH_MM.format(slot.getStartTime());
        String end = slot.getEndTime() == null ? "" : HH_MM.format(slot.getEndTime());
        return start + "-" + end;
    }

    private String weekdayCN(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "周一";
            case TUESDAY -> "周二";
            case WEDNESDAY -> "周三";
            case THURSDAY -> "周四";
            case FRIDAY -> "周五";
            case SATURDAY -> "周六";
            case SUNDAY -> "周日";
        };
    }

    private String statusLabel(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "UNTAKEN" -> "未上";
            case "NORMAL" -> "正常上课";
            case "ABSENT" -> "顺延";
            case "MADEUP" -> "已补";
            case "CANCELLED" -> "作废";
            default -> status;
        };
    }

    private String describe(AiActionDTO a) {
        if (a.getNote() != null && !a.getNote().isBlank()) {
            return a.getNote();
        }
        String who = a.getStudentName() == null ? "该课次" : a.getStudentName();
        String when = a.getLessonDate() == null ? "" : (" " + a.getLessonDate());
        String slot = a.getSlotLabel() == null ? "" : (" " + a.getSlotLabel());
        return who + when + slot;
    }

    private String blankToDash(String v) {
        return v == null || v.isBlank() ? "-" : v;
    }
}
