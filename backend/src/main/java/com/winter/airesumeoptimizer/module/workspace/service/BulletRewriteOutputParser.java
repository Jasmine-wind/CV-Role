package com.winter.airesumeoptimizer.module.workspace.service;

import com.winter.airesumeoptimizer.module.workspace.dto.BulletRewriteOutputDTO;

/**
 * Bullet 改写 AI 输出解析边界。
 *
 * <p>AI 输出不可信：malformed / empty / oversized / truncated / refusal 全部 fail closed。
 * malformed 抛出 {@link com.winter.airesumeoptimizer.common.exception.BusinessException}；
 * AI 明确拒绝时抛出 {@link BulletRewriteRefusedException}，由服务层转成 REJECTED。
 */
public interface BulletRewriteOutputParser {

    BulletRewriteOutputDTO parse(String aiOutput);
}
