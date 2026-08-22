package com.winter.airesumeoptimizer.module.ai.credential.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiCredentialUpsertRequestDTO {

    @NotBlank(message = "Base URL 不能为空")
    @Size(max = 2048, message = "Base URL 过长")
    private String baseUrl;

    @NotBlank(message = "API Key 不能为空")
    @Size(max = 4096, message = "API Key 过长")
    private String apiKey;

    @NotBlank(message = "模型名称不能为空")
    @Size(max = 200, message = "模型名称过长")
    private String model;

    private Map<String, Object> config;
}
