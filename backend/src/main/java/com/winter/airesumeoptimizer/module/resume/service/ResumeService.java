package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.infra.ai.AiSelectionSnapshot;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeDisplayNameUpdateRequestDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeParseOptionsDTO;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeUploadVO;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeDetailVO;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeListVO;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeParseResultVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface ResumeService {

    ResumeUploadVO upload(Long userId, MultipartFile file);

    List<ResumeListVO> listByUser(Long userId);

    ResumeDetailVO getDetail(Long userId, Long resumeId);

    ResumeDetailVO updateDisplayName(Long userId, Long resumeId, ResumeDisplayNameUpdateRequestDTO request);

    ResumeParseResultVO parse(Long userId, Long resumeId);

    ResumeParseResultVO parse(Long userId, Long resumeId, ResumeParseOptionsDTO options);

    default ResumeParseResultVO parseWithSelection(
            Long userId,
            Long resumeId,
            AiSelectionSnapshot selection) {
        return parse(userId, resumeId);
    }

    default ResumeParseResultVO parseWithSelection(
            Long userId,
            Long resumeId,
            ResumeParseOptionsDTO options,
            AiSelectionSnapshot selection) {
        return parse(userId, resumeId, options);
    }

    ResumeParseResultVO getParseResult(Long userId, Long resumeId);

    void delete(Long userId, Long resumeId);
}
