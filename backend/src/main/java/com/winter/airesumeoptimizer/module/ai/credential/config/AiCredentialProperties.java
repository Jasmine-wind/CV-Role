package com.winter.airesumeoptimizer.module.ai.credential.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai.credentials")
public class AiCredentialProperties {

    private boolean enabled;
    private String activeKeyId = "v1";
    /** keyId=base64url-or-base64;keyId2=... */
    private String keyRing = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getActiveKeyId() {
        return activeKeyId;
    }

    public void setActiveKeyId(String activeKeyId) {
        this.activeKeyId = activeKeyId;
    }

    public String getKeyRing() {
        return keyRing;
    }

    public void setKeyRing(String keyRing) {
        this.keyRing = keyRing;
    }
}
