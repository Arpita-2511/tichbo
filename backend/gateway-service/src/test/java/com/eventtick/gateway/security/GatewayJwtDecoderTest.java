package com.eventtick.gateway.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayJwtDecoderTest {

    @Test
    void secretShorterThan32Bytes_isRejectedAtStartup() {
        // Same floor user-service enforces; failing fast beats silently
        // validating against a weak key.
        assertThatThrownBy(() -> GatewayJwtDecoder.create("too-short", "iss", "aud"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes")
                // The message must describe the rule, never echo the secret.
                .hasMessageNotContaining("too-short");
    }
}
