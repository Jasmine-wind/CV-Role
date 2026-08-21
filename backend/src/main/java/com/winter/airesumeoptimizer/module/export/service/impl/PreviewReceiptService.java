package com.winter.airesumeoptimizer.module.export.service.impl;

import com.winter.airesumeoptimizer.module.export.service.PreviewReceiptClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 短期、无状态、服务端签名的 Preview → Export receipt。 */
@Component
public class PreviewReceiptService {

    private static final String SUBJECT = "resume-preview";
    private final SecretKey secretKey;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public PreviewReceiptService(
            @Value("${jwt.secret}") String secret,
            @Value("${app.render.preview-receipt-ttl:10m}") Duration ttl) {
        this(secret, ttl, Clock.systemUTC());
    }

    PreviewReceiptService(String secret, Duration ttl, Clock clock) {
        this.secretKey = Keys.hmacShaKeyFor(deriveKey(secret));
        this.ttl = ttl;
        this.clock = clock;
    }

    public String issue(PreviewReceiptClaims claims) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(SUBJECT)
                .claim("uid", claims.userId())
                .claim("task", claims.optimizationTaskId())
                .claim("target", claims.targetResumeVersionId())
                .claim("revision", claims.contentRevision())
                .claim("template", claims.templateId())
                .claim("templateVersion", claims.templateVersion())
                .claim("renderer", claims.rendererVersion())
                .claim("checksum", claims.pdfChecksum())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(secretKey)
                .compact();
    }

    public PreviewReceiptClaims verify(String receipt) {
        if (receipt == null || receipt.isBlank()) {
            throw new IllegalArgumentException("preview receipt is missing");
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(receipt)
                    .getPayload();
            if (!SUBJECT.equals(claims.getSubject())) {
                throw new IllegalArgumentException("preview receipt subject is invalid");
            }
            return new PreviewReceiptClaims(
                    number(claims, "uid"),
                    number(claims, "task"),
                    number(claims, "target"),
                    number(claims, "revision"),
                    text(claims, "template"),
                    text(claims, "templateVersion"),
                    text(claims, "renderer"),
                    text(claims, "checksum"));
        } catch (JwtException | ClassCastException exception) {
            throw new IllegalArgumentException("preview receipt is invalid", exception);
        }
    }

    private long number(Claims claims, String name) {
        Object value = claims.get(name);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("preview receipt claim is invalid");
        }
        return number.longValue();
    }

    private String text(Claims claims, String name) {
        Object value = claims.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("preview receipt claim is invalid");
        }
        return text;
    }

    private static byte[] deriveKey(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(
                    (secret + "\u0000cv-role-phase6-preview-receipt").getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
