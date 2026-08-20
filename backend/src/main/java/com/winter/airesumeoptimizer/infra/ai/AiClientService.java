package com.winter.airesumeoptimizer.infra.ai;

import java.util.List;

public interface AiClientService {

    /**
     * 按角色分离的消息列表执行补全。
     * SYSTEM 消息只放平台可信策略；USER 消息放不可信输入数据。
     */
    String complete(List<AiChatMessage> messages);

    /** 便捷入口：整段输入作为单条 USER 消息。 */
    default String complete(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new AiClientException("AI 输入不能为空");
        }
        return complete(List.of(AiChatMessage.user(prompt)));
    }

    String modelName();
}
