package com.winter.airesumeoptimizer.module.workspace.service;

/**
 * AI 明确表示无法在不新增事实的情况下改写。属于保守防线的正常结果，不是系统错误。
 */
public class BulletRewriteRefusedException extends RuntimeException {

    public BulletRewriteRefusedException(String message) {
        super(message);
    }
}
