package com.lesson.schedule.common;

import java.io.Serializable;
import lombok.Data;

/**
 * 统一返回体（A-17）。所有 REST 接口统一为 {@code {code, message, data}}。
 * <ul>
 *   <li>成功：code = 0，message = "success"，data 为业务数据</li>
 *   <li>失败：code 为业务/HTTP 状态码（如 400/409/500），message 为提示</li>
 * </ul>
 */
@Data
public class Result<T> implements Serializable {

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
