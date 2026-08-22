package com.winter.airesumeoptimizer.module.ai.credential.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.winter.airesumeoptimizer.infra.ai.AiGatewayException;
import com.winter.airesumeoptimizer.module.ai.credential.config.AiCredentialProperties;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmCredentialCipherTest {

    private static final Long USER_ID = 7L;
    private static final Long CREDENTIAL_ID = 11L;

    @Test
    void shouldRoundTripWithCredentialBoundAad() {
        AiCredentialProperties properties = properties("v1", "v1", (byte) 1);
        AesGcmCredentialCipher cipher = new AesGcmCredentialCipher(properties);

        CredentialCipher.EncryptedValue encrypted = cipher.encrypt("synthetic-api-key", USER_ID, CREDENTIAL_ID);

        assertThat(encrypted.envelope()).startsWith("enc:v1:v1:");
        assertThat(encrypted.envelope()).doesNotContain("synthetic-api-key");
        assertThat(cipher.decrypt(encrypted.envelope(), USER_ID, CREDENTIAL_ID))
                .isEqualTo("synthetic-api-key");
        assertThatCode(() -> cipher.validateEnvelopeKeyVersion(encrypted.envelope(), "v1"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> cipher.validateEnvelopeKeyVersion(encrypted.envelope(), "other"))
                .isInstanceOf(AiGatewayException.class);
    }

    @Test
    void shouldFailClosedForTamperingWrongOwnerAndUnknownKey() {
        AiCredentialProperties properties = properties("v1", "v1", (byte) 2);
        AesGcmCredentialCipher cipher = new AesGcmCredentialCipher(properties);
        String envelope = cipher.encrypt("synthetic-api-key", USER_ID, CREDENTIAL_ID).envelope();

        String[] parts = envelope.split(":", -1);
        String tampered = parts[0] + ":" + parts[1] + ":" + parts[2] + ":" + parts[3]
                + ":" + flipLastCharacter(parts[4]);
        assertThatThrownBy(() -> cipher.decrypt(tampered, USER_ID, CREDENTIAL_ID))
                .isInstanceOf(AiGatewayException.class);
        assertThatThrownBy(() -> cipher.decrypt(envelope, USER_ID + 1, CREDENTIAL_ID))
                .isInstanceOf(AiGatewayException.class);

        String unknownKey = envelope.replace(":v1:", ":missing-key:");
        assertThatThrownBy(() -> cipher.decrypt(unknownKey, USER_ID, CREDENTIAL_ID))
                .isInstanceOf(AiGatewayException.class);
    }

    @Test
    void shouldDecryptOldKeyDuringKeyRotationWithoutChangingRevision() {
        AiCredentialProperties oldProperties = properties("old", "old", (byte) 3);
        AesGcmCredentialCipher oldCipher = new AesGcmCredentialCipher(oldProperties);
        String envelope = oldCipher.encrypt("rotating-key", USER_ID, CREDENTIAL_ID).envelope();

        AiCredentialProperties rotated = new AiCredentialProperties();
        rotated.setEnabled(true);
        rotated.setActiveKeyId("new");
        rotated.setKeyRing("old=" + encoded((byte) 3) + ";new=" + encoded((byte) 4));
        AesGcmCredentialCipher rotatedCipher = new AesGcmCredentialCipher(rotated);

        assertThat(rotatedCipher.decrypt(envelope, USER_ID, CREDENTIAL_ID)).isEqualTo("rotating-key");
        assertThat(rotatedCipher.encrypt("new-key", USER_ID, CREDENTIAL_ID).keyVersion()).isEqualTo("new");
    }

    @Test
    void shouldRejectDisabledOrMalformedKeyRing() {
        AiCredentialProperties disabled = properties("v1", "v1", (byte) 5);
        disabled.setEnabled(false);
        AesGcmCredentialCipher disabledCipher = new AesGcmCredentialCipher(disabled);
        assertThatThrownBy(() -> disabledCipher.encrypt("key", USER_ID, CREDENTIAL_ID))
                .isInstanceOf(AiGatewayException.class);

        AiCredentialProperties malformed = new AiCredentialProperties();
        malformed.setEnabled(true);
        malformed.setActiveKeyId("v1");
        malformed.setKeyRing("v1=not-a-256-bit-key");
        assertThatThrownBy(() -> new AesGcmCredentialCipher(malformed))
                .isInstanceOf(AiGatewayException.class);
    }

    @Test
    void shouldRejectMissingActiveKeyDuplicateKeyIdAndUnsafeKeyIdAtStartup() {
        AiCredentialProperties missingActive = new AiCredentialProperties();
        missingActive.setEnabled(true);
        missingActive.setActiveKeyId("new");
        missingActive.setKeyRing("old=" + encoded((byte) 7));
        assertThatThrownBy(() -> new AesGcmCredentialCipher(missingActive))
                .isInstanceOf(AiGatewayException.class);

        AiCredentialProperties duplicate = new AiCredentialProperties();
        duplicate.setEnabled(true);
        duplicate.setActiveKeyId("v1");
        duplicate.setKeyRing("v1=" + encoded((byte) 8) + ";v1=" + encoded((byte) 9));
        assertThatThrownBy(() -> new AesGcmCredentialCipher(duplicate))
                .isInstanceOf(AiGatewayException.class);

        AiCredentialProperties unsafe = new AiCredentialProperties();
        unsafe.setEnabled(true);
        unsafe.setActiveKeyId("v:1");
        unsafe.setKeyRing("v:1=" + encoded((byte) 10));
        assertThatThrownBy(() -> new AesGcmCredentialCipher(unsafe))
                .isInstanceOf(AiGatewayException.class);
    }

    private AiCredentialProperties properties(String active, String keyId, byte seed) {
        AiCredentialProperties properties = new AiCredentialProperties();
        properties.setEnabled(true);
        properties.setActiveKeyId(active);
        properties.setKeyRing(keyId + "=" + encoded(seed));
        return properties;
    }

    private String encoded(byte seed) {
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) {
            key[index] = (byte) (seed + index);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key);
    }

    private String flipLastCharacter(String value) {
        char last = value.charAt(value.length() - 1);
        return value.substring(0, value.length() - 1) + (last == 'A' ? 'B' : 'A');
    }
}
