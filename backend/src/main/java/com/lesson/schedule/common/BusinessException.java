package com.lesson.schedule.common;

/**
 * 业务异常：携带业务/HTTP 状态码，交由 {@link GlobalExceptionHandler} 统一转换为 {@link Result}。
 * 约定：参数类错误 code=400；业务冲突（重叠/重复/删除保护）code=409；其它未预期异常 code=500。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
