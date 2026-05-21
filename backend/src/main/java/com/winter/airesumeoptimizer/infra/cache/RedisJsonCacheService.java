package com.winter.airesumeoptimizer.infra.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.logging.LogSanitizer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisJsonCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisJsonCacheService.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisCacheProperties properties;

    public RedisJsonCacheService(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            RedisCacheProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public <T> Optional<T> get(String key, Class<T> valueType) {
        if (!properties.isEnabled() || key == null || key.isBlank()) {
            return Optional.empty();
        }
        try {
            String value = stringRedisTemplate.opsForValue().get(normalizeKey(key));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, valueType));
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Redis cache read ignored: keyDigest={}, reason={}",
                    digest(key),
                    LogSanitizer.sanitize(exception.getMessage()));
            return Optional.empty();
        }
    }

    public void put(String key, Object value, long ttlSeconds) {
        if (!properties.isEnabled() || key == null || key.isBlank() || value == null) {
            return;
        }
        try {
            long effectiveTtl = ttlSeconds > 0 ? ttlSeconds : properties.getDefaultTtlSeconds();
            stringRedisTemplate.opsForValue().set(normalizeKey(key), objectMapper.writeValueAsString(value), Duration.ofSeconds(effectiveTtl));
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Redis cache write ignored: keyDigest={}, reason={}",
                    digest(key),
                    LogSanitizer.sanitize(exception.getMessage()));
        }
    }

    public long aiDisplayModelTtlSeconds() {
        return properties.getAiDisplayModelTtlSeconds();
    }

    private String normalizeKey(String key) {
        String prefix = properties.getKeyPrefix();
        if (prefix == null || prefix.isBlank()) {
            return key;
        }
        return prefix + ":" + key;
    }

    private String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
