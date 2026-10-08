package com.lesson.schedule.common.vo;

import java.util.List;
import lombok.Data;

/**
 * AI 解析结果的包装体。
 * <p>Spring AI 的 {@code .entity(Class)} 需要具体类型，不能直接反序列化成 List，故包一层。</p>
 */
@Data
public class AiParsedQuestionsVO {

    private List<AiParsedQuestionVO> questions;
}
