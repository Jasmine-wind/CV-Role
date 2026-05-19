package com.winter.airesumeoptimizer.module.resume.service;

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

    ResumeParseResultVO parse(Long userId, Long resumeId);

    ResumeParseResultVO parse(Long userId, Long resumeId, ResumeParseOptionsDTO options);

    ResumeParseResultVO getParseResult(Long userId, Long resumeId);

    void delete(Long userId, Long resumeId);
}
