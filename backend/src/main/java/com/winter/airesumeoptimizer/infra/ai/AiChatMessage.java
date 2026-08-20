package com.winter.airesumeoptimizer.infra.ai;

/**
 * 角色分离的 AI 消息。
 *
 * <p>SYSTEM 承载平台可信策略（真实性约束、输出 Schema、安全指令），
 * USER 承载不可信数据（简历、JD、证据、用户本次要求）。两者在请求体中显式分离，
 * 不可信内容不得混入 SYSTEM 消息。
 */
public record AiChatMessage(Role role, String content) {

    public enum Role {
        SYSTEM,
        USER
    }

    public static AiChatMessage system(String content) {
        return new AiChatMessage(Role.SYSTEM, content);
    }

    public static AiChatMessage user(String content) {
        return new AiChatMessage(Role.USER, content);
    }
}
