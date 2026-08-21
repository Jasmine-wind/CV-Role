package com.winter.airesumeoptimizer.infra.render;

/**
 * 渲染链路失败（Schema 无法映射、模板缺失、Typst 编译失败或超时）。
 * 渲染失败只影响本次 Preview / Export，不触碰 Workspace 已保存内容。
 */
public class ResumeRenderException extends RuntimeException {

    public ResumeRenderException(String message) {
        super(message);
    }

    public ResumeRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
