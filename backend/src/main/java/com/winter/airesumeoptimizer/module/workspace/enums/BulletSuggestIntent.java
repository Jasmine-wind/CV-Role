package com.winter.airesumeoptimizer.module.workspace.enums;

/**
 * 单 Bullet 岗位定向改写意图。所有意图都受同一事实闭包校验约束。
 */
public enum BulletSuggestIntent {

    /** 岗位定向优化：强调与目标岗位相关的表达。 */
    JOB_TARGETED,

    /** 精简：去掉冗余表达。 */
    SIMPLIFY,

    /** 强化技术深度：改善技术表达，不新增技术事实。 */
    TECHNICAL_DEPTH,

    /** 突出成果：改善成果表达，不新增量化结果。 */
    HIGHLIGHT_OUTCOME,

    /** 自定义要求：附带用户本次要求（不可信输入，不得覆盖平台约束）。 */
    CUSTOM
}
