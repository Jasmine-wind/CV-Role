package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.storage.FileStorageService;
import com.winter.airesumeoptimizer.infra.storage.StoredFile;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.resume.service.ResumeService;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeDetailVO;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeListVO;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeUploadVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResumeServiceImpl implements ResumeService {

    private static final String UPLOAD_STATUS_UPLOADED = "UPLOADED";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx");
    private static final Set<String> PDF_CONTENT_TYPES = Set.of("application/pdf");

    private final ResumeMapper resumeMapper;
    private final FileStorageService fileStorageService;
    private final long maxFileSize;

    public ResumeServiceImpl(
            ResumeMapper resumeMapper,
            FileStorageService fileStorageService,
            @Value("${app.resume.upload.max-file-size-bytes:10485760}") long maxFileSize) {
        this.resumeMapper = resumeMapper;
        this.fileStorageService = fileStorageService;
        this.maxFileSize = maxFileSize;
    }

    @Override
    @Transactional
    public ResumeUploadVO upload(Long userId, MultipartFile file) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        validateFile(file);

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String fileType = extractFileType(originalFilename);
        StoredFile storedFile = fileStorageService.store(file, "resumes/" + userId);

        Resume resume = buildResume(userId, storedFile, fileType);
        try {
            int rows = resumeMapper.insert(resume);
            if (rows != 1 || resume.getId() == null) {
                throw new BusinessException(500, "简历元数据保存失败");
            }
        } catch (RuntimeException exception) {
            fileStorageService.delete(storedFile.objectKey());
            throw exception;
        }

        return ResumeUploadVO.builder()
                .id(resume.getId())
                .originalFilename(resume.getOriginalFilename())
                .fileType(resume.getFileType())
                .fileSize(resume.getFileSize())
                .objectKey(resume.getObjectKey())
                .uploadStatus(resume.getUploadStatus())
                .createdAt(resume.getCreatedAt())
                .build();
    }

    @Override
    public List<ResumeListVO> listByUser(Long userId) {
        validateUserId(userId);

        return resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                        .eq(Resume::getUserId, userId)
                        .orderByDesc(Resume::getCreatedAt))
                .stream()
                .map(this::toListVO)
                .toList();
    }

    @Override
    public ResumeDetailVO getDetail(Long userId, Long resumeId) {
        validateUserId(userId);
        if (resumeId == null) {
            throw new BusinessException(400, "简历 ID 不能为空");
        }

        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getId, resumeId)
                .eq(Resume::getUserId, userId));
        if (resume == null) {
            throw new BusinessException(404, "简历不存在");
        }

        return toDetailVO(resume);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择要上传的简历文件");
        }
        if (file.getSize() > maxFileSize) {
            throw new BusinessException(400, "简历文件大小不能超过 " + maxFileSize + " 字节");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String fileType = extractFileType(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(fileType)) {
            throw new BusinessException(400, "仅支持 PDF、DOC、DOCX 简历文件");
        }

        if ("pdf".equals(fileType) && !isAllowedPdfContentType(file.getContentType())) {
            throw new BusinessException(400, "文件类型与扩展名不匹配");
        }
    }

    private boolean isAllowedPdfContentType(String contentType) {
        return contentType != null && PDF_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT));
    }

    private Resume buildResume(Long userId, StoredFile storedFile, String fileType) {
        LocalDateTime now = LocalDateTime.now();
        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setOriginalFilename(storedFile.originalFilename());
        resume.setFileType(fileType.toUpperCase(Locale.ROOT));
        resume.setFileSize(storedFile.size());
        resume.setObjectKey(storedFile.objectKey());
        resume.setUploadStatus(UPLOAD_STATUS_UPLOADED);
        resume.setCreatedAt(now);
        resume.setUpdatedAt(now);
        return resume;
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
    }

    private ResumeListVO toListVO(Resume resume) {
        return ResumeListVO.builder()
                .id(resume.getId())
                .originalFilename(resume.getOriginalFilename())
                .fileType(resume.getFileType())
                .fileSize(resume.getFileSize())
                .uploadStatus(resume.getUploadStatus())
                .createdAt(resume.getCreatedAt())
                .build();
    }

    private ResumeDetailVO toDetailVO(Resume resume) {
        return ResumeDetailVO.builder()
                .id(resume.getId())
                .originalFilename(resume.getOriginalFilename())
                .fileType(resume.getFileType())
                .fileSize(resume.getFileSize())
                .uploadStatus(resume.getUploadStatus())
                .createdAt(resume.getCreatedAt())
                .updatedAt(resume.getUpdatedAt())
                .build();
    }

    private String extractFileType(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new BusinessException(400, "文件名不能为空");
        }

        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            throw new BusinessException(400, "文件缺少扩展名");
        }

        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }
}
