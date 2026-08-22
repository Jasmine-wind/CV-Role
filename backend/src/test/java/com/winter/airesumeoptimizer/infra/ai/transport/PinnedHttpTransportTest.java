package com.winter.airesumeoptimizer.infra.ai.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.junit.jupiter.api.Test;

class PinnedHttpTransportTest {

    @Test
    void shouldDecodeSmallResponse() throws Exception {
        PinnedHttpTransport transport = new PinnedHttpTransport();

        String body = transport.readBoundedBody(new ByteArrayEntity(
                "{\"ok\":true}".getBytes(StandardCharsets.UTF_8),
                ContentType.APPLICATION_JSON));

        assertThat(body).isEqualTo("{\"ok\":true}");
    }

    @Test
    void shouldAllowResponseAtExactOneMiBLimit() throws Exception {
        PinnedHttpTransport transport = new PinnedHttpTransport();
        byte[] exact = new byte[PinnedHttpTransport.MAX_RESPONSE_BYTES];

        String body = transport.readBoundedBody(new ByteArrayEntity(exact, ContentType.APPLICATION_JSON));

        assertThat(body.getBytes(StandardCharsets.UTF_8)).hasSize(PinnedHttpTransport.MAX_RESPONSE_BYTES);
    }

    @Test
    void shouldRejectResponseAboveOneMiBBeforeReturningIt() {
        PinnedHttpTransport transport = new PinnedHttpTransport();
        byte[] oversized = new byte[PinnedHttpTransport.MAX_RESPONSE_BYTES + 1];

        assertThatThrownBy(() -> transport.readBoundedBody(new ByteArrayEntity(oversized, ContentType.APPLICATION_JSON)))
                .isInstanceOf(OutboundTransportException.class)
                .extracting(exception -> ((OutboundTransportException) exception).getKind())
                .isEqualTo(OutboundTransportException.Kind.RESPONSE_TOO_LARGE);
    }
}
