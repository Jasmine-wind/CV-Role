package com.winter.airesumeoptimizer.module.resume.service;

/** Durable resume-scoped parse ownership used to coordinate workers across application nodes. */
public interface ResumeParseClaimService {

    ResumeParseClaim acquire(Long resumeId);

    record ResumeParseClaim(Long generation, String token) {
    }
}
