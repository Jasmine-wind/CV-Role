package com.winter.airesumeoptimizer.infra.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class PromptTemplateService {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String render(String resourcePath, Map<String, String> variables) {
        String template = cache.computeIfAbsent(resourcePath, this::loadTemplate);
        String rendered = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", nullToEmpty(entry.getValue()));
        }
        return rendered.strip();
    }

    private String loadTemplate(String resourcePath) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Prompt 模板加载失败：" + resourcePath, exception);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
