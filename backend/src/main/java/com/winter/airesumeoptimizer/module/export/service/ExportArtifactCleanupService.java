package com.winter.airesumeoptimizer.module.export.service;

/**
 * 导出物删除与父资源级联前清理 seam。
 *
 * <p>单个导出物删除采用持久化 DELETE_PENDING → 对象删除 → 元数据删除。父资源删除则先持久化
 * DELETE_PENDING 并删除对象，保留元数据到父事务成功级联；这样父事务回滚时仍有可重试依据。
 */
public interface ExportArtifactCleanupService {

    void deleteArtifact(Long userId, Long artifactId);

    /** 父级删除前清理对象；元数据留给父事务成功时的外键级联。 */
    void deleteArtifactsForResume(Long userId, Long resumeId);

    /** 清理单个岗位优化任务生成的 PDF 派生物，供任务删除前调用。 */
    void deleteArtifactsForOptimizationTask(Long userId, Long optimizationTaskId);

    /** 父级删除前清理对象；元数据留给父事务成功时的外键级联。 */
    void deleteArtifactsForJobDescription(Long userId, Long jobDescriptionId);
}
