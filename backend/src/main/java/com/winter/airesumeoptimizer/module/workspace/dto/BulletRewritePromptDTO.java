package com.winter.airesumeoptimizer.module.workspace.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Bullet 改写 Prompt：SYSTEM 是平台可信策略，USER 内容承载全部不可信数据。
 */
@Getter
@Builder
public class BulletRewritePromptDTO {

    private final String promptVersion;
    private final String systemPolicy;
    private final String userContent;
}
