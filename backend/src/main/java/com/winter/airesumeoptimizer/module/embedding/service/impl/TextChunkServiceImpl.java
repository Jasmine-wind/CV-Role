package com.winter.airesumeoptimizer.module.embedding.service.impl;

import com.winter.airesumeoptimizer.module.embedding.service.TextChunkService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TextChunkServiceImpl implements TextChunkService {

    private static final int MAX_CHUNK_LENGTH = 1200;
    private static final int MAX_CHUNK_COUNT = 8;

    @Override
    public List<String> splitResumeText(String structuredJson, String extractedText) {
        List<String> chunks = new ArrayList<>();

        String normalizedStructuredJson = normalize(structuredJson);
        if (!normalizedStructuredJson.isBlank()) {
            addChunk(chunks, "简历结构化解析\n" + normalizedStructuredJson);
        }
        splitParagraphs(chunks, extractedText);

        return chunks.stream()
                .filter(chunk -> chunk != null && !chunk.isBlank())
                .limit(MAX_CHUNK_COUNT)
                .toList();
    }

    @Override
    public List<String> splitJobDescriptionText(String structuredContent, String rawText) {
        List<String> chunks = new ArrayList<>();

        String normalizedStructuredContent = normalize(structuredContent);
        if (!normalizedStructuredContent.isBlank()) {
            addChunk(chunks, "岗位描述结构化解析\n" + normalizedStructuredContent);
        }
        splitParagraphs(chunks, rawText);

        return chunks.stream()
                .filter(chunk -> chunk != null && !chunk.isBlank())
                .limit(MAX_CHUNK_COUNT)
                .toList();
    }

    private void splitParagraphs(List<String> chunks, String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return;
        }

        StringBuilder current = new StringBuilder();
        for (String paragraph : normalized.split("\\n\\s*\\n")) {
            String cleanParagraph = paragraph.strip();
            if (cleanParagraph.isBlank()) {
                continue;
            }
            if (current.length() + cleanParagraph.length() + 2 > MAX_CHUNK_LENGTH) {
                addChunk(chunks, current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(cleanParagraph);
        }
        addChunk(chunks, current.toString());
    }

    private void addChunk(List<String> chunks, String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return;
        }
        if (normalized.length() <= MAX_CHUNK_LENGTH) {
            chunks.add(normalized);
            return;
        }

        int start = 0;
        while (start < normalized.length() && chunks.size() < MAX_CHUNK_COUNT) {
            int end = Math.min(start + MAX_CHUNK_LENGTH, normalized.length());
            chunks.add(normalized.substring(start, end).strip());
            start = end;
        }
    }

    private String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.strip()
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t ]+", " ");
    }
}
