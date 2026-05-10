package com.winter.airesumeoptimizer.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String JWT_SECURITY_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI aiResumeOptimizerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Resume Optimizer API")
                        .version("v1.7")
                        .description("AI 简历优化与岗位匹配系统 API 文档")
                        .contact(new Contact().name("Winter")))
                .components(new Components().addSecuritySchemes(
                        JWT_SECURITY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录后将返回的 token 以 Bearer Token 形式填入 Authorization 请求头")))
                .addSecurityItem(new SecurityRequirement().addList(JWT_SECURITY_SCHEME));
    }
}
