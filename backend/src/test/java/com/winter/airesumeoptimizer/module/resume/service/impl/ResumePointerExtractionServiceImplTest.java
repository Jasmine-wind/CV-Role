package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.infra.ai.AiChatMessage;
import com.winter.airesumeoptimizer.infra.ai.AiClientService;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeIndexedLineDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeParseMode;
import com.winter.airesumeoptimizer.module.resume.dto.ResumePointerExtractionResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumePointerExtractorType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumePointerExtractionServiceImplTest {

    @Test
    void fastModeShouldNotInvokeAi() {
        CountingAiClient aiClient = new CountingAiClient("{}");
        ResumePointerExtractionServiceImpl service = new ResumePointerExtractionServiceImpl(aiClient, new ObjectMapper());

        ResumePointerExtractionResultDTO result = service.extract(1L, lines(), ResumeParseMode.FAST, ResumePointerExtractorType.WORK_EXPERIENCE);

        assertThat(result.getAiInvoked()).isFalse();
        assertThat(result.getWorkExperiencePointers()).isEmpty();
        assertThat(aiClient.callCount).isZero();
    }

    @Test
    void extractShouldSanitizeInvalidLineIdsAndCacheLegalResult() {
        CountingAiClient aiClient = new CountingAiClient("""
                {
                  "workExperiencePointers": [
                    {"companyLine": 1, "positionLine": 3, "timeLine": 4, "descriptionLineRange": [2, 4], "confidence": 0.86},
                    {"companyLine": 99, "positionLine": 3, "timeLine": 4, "descriptionLineRange": [2, 99], "confidence": 0.9}
                  ],
                  "projectPointers": [
                    {"nameLine": 2, "descriptionLineRange": [2, 4], "confidence": 0.9}
                  ]
                }
                """);
        ResumePointerExtractionServiceImpl service = new ResumePointerExtractionServiceImpl(aiClient, new ObjectMapper());

        ResumePointerExtractionResultDTO first = service.extract(1L, lines(), ResumeParseMode.ACCURATE, ResumePointerExtractorType.WORK_EXPERIENCE);
        ResumePointerExtractionResultDTO second = service.extract(1L, lines(), ResumeParseMode.ACCURATE, ResumePointerExtractorType.WORK_EXPERIENCE);

        assertThat(first.getAiInvoked()).isTrue();
        assertThat(first.getCacheHit()).isFalse();
        assertThat(first.getWorkExperiencePointers()).hasSize(1);
        assertThat(first.getWorkExperiencePointers().get(0).getCompanyLine()).isEqualTo(1);
        assertThat(first.getProjectPointers()).isEmpty();
        assertThat(second.getAiInvoked()).isFalse();
        assertThat(second.getCacheHit()).isTrue();
        assertThat(second.getWorkExperiencePointers()).hasSize(1);
        assertThat(aiClient.callCount).isEqualTo(1);
    }

    private List<ResumeIndexedLineDTO> lines() {
        return List.of(
                line(1, "北京华来知识科技有限公司"),
                line(2, "负责公司软件产品和项目开发"),
                line(3, "JavaEE 软件工程师"),
                line(4, "2017.10 - 2019.09"));
    }

    private ResumeIndexedLineDTO line(int id, String text) {
        return ResumeIndexedLineDTO.builder()
                .lineId(id)
                .text(text)
                .normalizedText(text)
                .sectionHint("WORK_EXPERIENCES")
                .isNoise(false)
                .build();
    }

    private static final class CountingAiClient implements AiClientService {

        private final String output;
        private int callCount;

        private CountingAiClient(String output) {
            this.output = output;
        }

        @Override
        public String complete(java.util.List<AiChatMessage> messages) {
            callCount++;
            return output;
        }

        @Override
        public String modelName() {
            return "test-model";
        }
    }
}
