package com.winter.airesumeoptimizer.module.ai.credential.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("ai_provider_credentials")
public class AiProviderCredential {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String providerType;

    private String baseUrl;

    private String encryptedApiKey;

    private String encryptionKeyVersion;

    private String model;

    private String configJson;

    private String status;

    private Long credentialRevision;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
