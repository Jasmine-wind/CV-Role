package com.winter.airesumeoptimizer.module.embedding.service;

import java.util.List;

public interface TextChunkService {

    List<String> splitResumeText(String structuredJson, String extractedText);

    List<String> splitJobDescriptionText(String structuredContent, String rawText);
}
