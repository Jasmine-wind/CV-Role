package com.winter.airesumeoptimizer.common.result;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "请求参数不正确"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    PAYLOAD_TOO_LARGE(413, "请求体过大"),
    AI_SERVICE_ERROR(502, "AI 服务调用失败"),
    INTERNAL_ERROR(500, "服务器内部错误");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
