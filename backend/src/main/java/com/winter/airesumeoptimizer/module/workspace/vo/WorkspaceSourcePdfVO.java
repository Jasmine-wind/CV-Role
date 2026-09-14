package com.winter.airesumeoptimizer.module.workspace.vo;

/** Authorized task-scoped SOURCE PDF payload; storage identity never crosses the API boundary. */
public record WorkspaceSourcePdfVO(byte[] bytes, String filename) {
    public WorkspaceSourcePdfVO {
        bytes = bytes == null ? new byte[0] : bytes.clone();
        filename = filename == null || filename.isBlank() ? "source-resume.pdf" : filename;
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
