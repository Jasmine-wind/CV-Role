package com.winter.airesumeoptimizer.module.resume.service;

/** Optional extraction seam used by the parser to retain layout hints without changing uploads. */
public interface ResumeLayoutAwareTextExtractionService {

    ResumeTextExtractionResult extractWithMetadata(String objectKey, String fileType);
}
