package com.winter.airesumeoptimizer.module.task.enums;

public enum AsyncTaskErrorCode {
    FILE_NOT_FOUND("文件不存在，请重新上传后再试"),
    FILE_READ_FAILED("文件读取失败，请确认文件可正常打开"),
    FILE_PARSE_FAILED("文件解析失败，请更换文件或稍后重试"),
    AI_TIMEOUT("AI 服务响应超时，请稍后重试"),
    AI_RESPONSE_INVALID("AI 返回结果格式异常，请稍后重试"),
    AI_JSON_PARSE_FAILED("AI 返回结果解析失败，请稍后重试"),
    AI_SERVICE_UNAVAILABLE("AI 服务暂时不可用，请稍后重试"),
    EMBEDDING_FAILED("向量生成失败，请稍后重试"),
    DATABASE_ERROR("结果保存失败，请稍后重试"),
    PERMISSION_DENIED("无权限执行该任务"),
    TASK_REJECTED("系统任务繁忙，请稍后重试"),
    UNKNOWN_ERROR("任务执行失败，请稍后重试");

    private final String userMessage;

    AsyncTaskErrorCode(String userMessage) {
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
