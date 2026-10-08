package com.lesson.schedule.common;

import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理（@RestControllerAdvice）。所有异常统一收敛为 {@link Result}。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：直接透传其状态码与提示。 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException ex) {
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    /** 请求体参数校验失败（@Valid / @Validated）。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("参数校验失败");
        return Result.fail(400, msg);
    }

    /** 非法参数（如手动抛出的 IllegalArgumentException）。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegal(IllegalArgumentException ex) {
        return Result.fail(400, ex.getMessage() == null ? "参数非法" : ex.getMessage());
    }

    /** 兜底：未预期异常统一 500。 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception ex) {
        return Result.fail(500, ex.getMessage() == null ? "服务器内部错误" : ex.getMessage());
    }
}
