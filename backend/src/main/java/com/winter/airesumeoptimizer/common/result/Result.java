package com.winter.airesumeoptimizer.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@Schema(description = "统一 API 响应")
public class Result<T> {

    @Schema(description = "业务状态码，200 表示成功", example = "200")
    private final Integer code;
    @Schema(description = "响应消息", example = "success")
    private final String message;
    @Schema(description = "响应数据")
    private final T data;
    @Schema(description = "错误请求路径")
    private final String path;
    @Schema(description = "错误发生时间")
    private final LocalDateTime timestamp;

    private Result(Integer code, String message, T data, String path, LocalDateTime timestamp) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.path = path;
        this.timestamp = timestamp;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data, null, null);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data, null, null);
    }

    public static <T> Result<T> failure(Integer code, String message) {
        return new Result<>(code, message, null, null, LocalDateTime.now());
    }

    public static <T> Result<T> failure(ResultCode resultCode) {
        return failure(resultCode, resultCode.getMessage());
    }

    public static <T> Result<T> failure(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null, null, LocalDateTime.now());
    }

    public static <T> Result<T> failure(ResultCode resultCode, String message, String path) {
        return new Result<>(resultCode.getCode(), message, null, path, LocalDateTime.now());
    }

    public static <T> Result<T> failure(Integer code, String message, String path) {
        return new Result<>(code, message, null, path, LocalDateTime.now());
    }
}
