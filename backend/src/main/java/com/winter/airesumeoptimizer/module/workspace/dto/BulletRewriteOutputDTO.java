package com.winter.airesumeoptimizer.module.workspace.dto;

/**
 * AI 改写输出解析结果。解析严格 fail closed：任何字段缺失、类型不符、超长或拒绝话术都不放行。
 */
public record BulletRewriteOutputDTO(String suggestedText, String reason) {
}
