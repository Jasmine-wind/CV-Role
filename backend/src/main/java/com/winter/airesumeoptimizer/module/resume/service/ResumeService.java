package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.vo.ResumeUploadVO;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeDetailVO;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeListVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface ResumeService {

    ResumeUploadVO upload(Long userId, MultipartFile file);

    List<ResumeListVO> listByUser(Long userId);

    ResumeDetailVO getDetail(Long userId, Long resumeId);
}
