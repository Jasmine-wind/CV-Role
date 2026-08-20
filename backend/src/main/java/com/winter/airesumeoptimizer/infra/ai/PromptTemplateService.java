package com.winter.airesumeoptimizer.infra.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class PromptTemplateService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * 单遍替换：占位符值不会被再次扫描，不可信值中的 {{...}} 字面量保持原样，
     * 杜绝先替换值对后续占位符的二次展开。
     */
    public String render(String resourcePath, Map<String, String> variables) {
        String template = cache.computeIfAbsent(resourcePath, this::loadTemplate);
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder rendered = new StringBuilder();
        int cursor = 0;
        while (matcher.find()) {
            rendered.append(template, cursor, matcher.start());
            rendered.append(nullToEmpty(variables.get(matcher.group(1))));
            cursor = matcher.end();
        }
        rendered.append(template, cursor, template.length());
        return rendered.toString().strip();
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
