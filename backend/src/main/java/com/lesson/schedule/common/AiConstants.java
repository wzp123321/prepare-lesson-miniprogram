package com.lesson.schedule.common;

/** AI 模块共用常量（排课助手与题库解析共用，避免提示语各写各的产生漂移）。 */
public final class AiConstants {

    /** application.yml 里未配置 Key 时的占位值：非空只是为了让应用能启动，不代表可用。 */
    public static final String API_KEY_PLACEHOLDER = "not-configured";

    private AiConstants() {
    }

    /** 判断 Key 是否真的配了（空串与占位值都算未配置）。 */
    public static boolean isApiKeyMissing(String apiKey) {
        return apiKey == null || apiKey.isBlank() || API_KEY_PLACEHOLDER.equals(apiKey.trim());
    }
}
