package com.winter.airesumeoptimizer.module.workspace.enums;

/**
 * Rewrite 事实校验违规类型。
 * 校验的是“事实闭包”而不是“词面闭包”：同义改写、语法调整和不改变事实的语言重组允许通过；
 * 新增或升级实体、技术、数字/量化结果、责任级别、成果、因果、范围、时间等事实声明必须拒绝。
 */
public enum RewriteFactViolationCode {

    /** 校验通过。 */
    OK,

    /** 建议文本为空或只有空白。 */
    EMPTY_OR_BLANK,

    /** 建议文本超过 Bullet 长度上限。 */
    OVERSIZED,

    /** 出现原文没有的数字 / 量化结果 / 倍数声明。 */
    NEW_QUANTITATIVE_CLAIM,

    /** 出现原文没有的技术、框架、工具或系统名称。 */
    NEW_TECHNOLOGY,

    /** 出现原文没有的实体（公司、产品、项目等专有名词）。 */
    NEW_ENTITY,

    /** 责任级别被升级，例如参与改为主导、开发改为负责人。 */
    RESPONSIBILITY_ESCALATION,

    /** 出现原文没有的成果、奖项、效果结论。 */
    NEW_ACHIEVEMENT,

    /** 出现原文没有的范围或时间事实，例如年份、公司级范围。 */
    NEW_SCOPE_OR_TIME,

    /** 出现疑似元素 ID / UUID，AI 不得生成结构化身份。 */
    ELEMENT_IDENTITY_LEAK,

    /** 无法可靠判断，按保守原则拒绝。 */
    UNDETERMINED
}
