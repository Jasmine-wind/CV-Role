package com.winter.airesumeoptimizer.module.workspace.enums;

import java.util.Arrays;

/**
 * 联系方式类型。Slice A 起联系方式必须携带显式类型，
 * 导出质量门与渲染均按类型判断，不再依赖自由文本 label 猜测。
 * 未知或无法判断的类型归一为 {@link #OTHER}，由用户确认修正。
 */
public enum ResumeDocumentContactType {

    PHONE("电话"),
    EMAIL("邮箱"),
    WECHAT("微信"),
    QQ("QQ"),
    LINKEDIN("LinkedIn"),
    GITHUB("GitHub"),
    WEBSITE("个人网站"),
    LOCATION("所在地"),
    OTHER("其他");

    private final String defaultLabel;

    ResumeDocumentContactType(String defaultLabel) {
        this.defaultLabel = defaultLabel;
    }

    public String getDefaultLabel() {
        return defaultLabel;
    }

    public static ResumeDocumentContactType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value.strip()))
                .findFirst()
                .orElse(OTHER);
    }
}
