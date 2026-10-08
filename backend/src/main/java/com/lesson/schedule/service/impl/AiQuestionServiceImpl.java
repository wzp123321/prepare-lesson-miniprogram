package com.lesson.schedule.service.impl;

import com.lesson.schedule.common.AiConstants;
import com.lesson.schedule.common.BusinessException;
import com.lesson.schedule.common.dto.AiParseQuestionDTO;
import com.lesson.schedule.common.vo.AiParsedQuestionVO;
import com.lesson.schedule.common.vo.AiParsedQuestionsVO;
import com.lesson.schedule.service.AiQuestionService;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * AI 题库助手实现（Spring AI → DeepSeek）。
 * <p>只做「补内容」：规则切题在前端完成，这里把每道题的原文整理成
 * 答案 / 解析 / 难度 / 知识点，供批量入库前核对。</p>
 */
@Service
public class AiQuestionServiceImpl implements AiQuestionService {

    /** 系统提示词：强调不改写原文、kpId 只能取自白名单、输出顺序与输入一致 */
    private static final String SYSTEM_PROMPT = """
            你是语文老师的题库助手。老师会给你若干道题的原始文字（可能含选项，也可能含答案与解析），
            请逐题整理成结构化数据。

            规则：
            1. stem：原样保留题干文字，不要改写、不要增删；多行内容用 \\n 连接，去掉题号前缀。
            2. options：选择题的选项数组，形如 ["A. 张三","B. 李四"]；不是选择题就返回空数组 []。
            3. qtype：只能取 选择 / 填空 / 判断 / 阅读 / 古诗文 / 写作 / 其他 之一。
            4. answer：原文给了答案就照抄原文；没给时按你的知识给出参考答案。
            5. analysis：讲题要点，一到三句话，说明这题考什么、上课怎么讲。
            6. difficulty：1 易 / 2 中 / 3 难。
            7. kpId：只能从【可选知识点】里挑最贴切的一个并填它的 id；都不贴切就填 null。
               严禁编造清单外的 id。
            8. questions 数组的长度与顺序必须与输入的题目一致，不要合并、不要遗漏。
            9. 只输出 JSON，格式：
               {"questions":[{"stem":"","qtype":"","options":[],"answer":"","analysis":"","difficulty":2,"kpId":null,"kpName":null}]}
            """;

    private final ChatClient chatClient;

    /** DeepSeek API Key：来自 spring.ai.openai.api-key（可直接写配置，也可由环境变量覆盖） */
    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    public AiQuestionServiceImpl(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Override
    public List<AiParsedQuestionVO> parse(AiParseQuestionDTO dto) {
        if (AiConstants.isApiKeyMissing(apiKey)) {
            throw new BusinessException(500,
                    "未配置 DeepSeek API Key：请在 application.yml 的 spring.ai.openai.api-key 填写，"
                            + "或设置环境变量 AI_DEEPSEEK_API_KEY 后重启后端");
        }
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException(400, "没有待解析的题目");
        }
        if (dto.getItems().size() > 30) {
            throw new BusinessException(400, "一次最多解析 30 道题，请分批处理");
        }

        AiParsedQuestionsVO parsed;
        try {
            parsed = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(buildUserMessage(dto))
                    .call()
                    .entity(AiParsedQuestionsVO.class);
        } catch (Exception e) {
            throw new BusinessException(500, "AI 解析失败：" + e.getMessage());
        }
        if (parsed == null || parsed.getQuestions() == null || parsed.getQuestions().isEmpty()) {
            throw new BusinessException(500, "AI 未返回解析结果，请重试或减少一次解析的题量");
        }
        return parsed.getQuestions();
    }

    /** 组装用户消息：年级 / 来源 / 知识点白名单 + 逐题原文 */
    private String buildUserMessage(AiParseQuestionDTO dto) {
        StringBuilder sb = new StringBuilder();
        if (dto.getGrade() != null && !dto.getGrade().isBlank()) {
            sb.append("【年级】").append(dto.getGrade()).append('\n');
        }
        if (dto.getSource() != null && !dto.getSource().isBlank()) {
            sb.append("【来源】").append(dto.getSource()).append('\n');
        }
        sb.append("【可选知识点】（只能从这里挑 kpId）\n");
        if (dto.getKpOptions() == null || dto.getKpOptions().isEmpty()) {
            sb.append("  （无可用知识点，全部填 null）\n");
        } else {
            for (AiParseQuestionDTO.KpRef kp : dto.getKpOptions()) {
                sb.append("  ").append(kp.getId()).append(" | ").append(kp.getName()).append('\n');
            }
        }
        sb.append("\n【待整理的题目】共 ").append(dto.getItems().size()).append(" 道\n");
        int i = 1;
        for (String item : dto.getItems()) {
            sb.append("---- 第 ").append(i++).append(" 题 ----\n")
                    .append(item == null ? "" : item).append('\n');
        }
        return sb.toString();
    }
}
