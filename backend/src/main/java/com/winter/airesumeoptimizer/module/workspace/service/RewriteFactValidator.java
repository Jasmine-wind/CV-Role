package com.winter.airesumeoptimizer.module.workspace.service;

import com.winter.airesumeoptimizer.module.workspace.dto.RewriteFactValidationResult;

/**
 * Rewrite 专用事实校验 seam。
 *
 * <p>校验目标是“事实闭包”：建议文本允许对 Bullet 原文做同义改写、语法调整与语言重组，
 * 但不得新增或升级任何事实声明（实体、技术、数字/量化结果、责任级别、成果、因果、范围、时间）。
 * 事实基线只取被改写 Bullet 自身的原文，不得跨 Bullet 搬运事实。
 *
 * <p>实现必须保守：无法可靠判断时 fail closed。不得把第二次 LLM 调用作为唯一真实性裁判。
 */
public interface RewriteFactValidator {

    RewriteFactValidationResult validate(String originalText, String suggestedText);
}
