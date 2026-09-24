package com.eventtick.gateway.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdWebFilterTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "b1946ac9-2c1d-4f0e-8a44-5a2a3c7f9e10",   // UUID
            "abc123",
            "req_2026-09-24.17:30:00",                 // letters, digits and . _ : -
            "4bf92f3577b34da6a3ce929d0e0e4736",        // W3C trace-id shape
    })
    void reasonableIds_areKept(String id) {
        assertThat(RequestIdWebFilter.resolve(id)).isEqualTo(id);
    }

    @Test
    void anIdOfExactlyTheMaximumLength_isKept() {
        String id = "a".repeat(128);
        assertThat(RequestIdWebFilter.resolve(id)).isEqualTo(id);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "has space",
            "line\r\nbreak",            // header/log injection
            "semi;colon",
            "<b>markup</b>",
            "unicodé",
            "%0d%0a",
    })
    void missingOrUnsafeIds_areReplacedByAGeneratedUuid(String id) {
        String resolved = RequestIdWebFilter.resolve(id);

        assertThat(resolved).isNotEqualTo(id);
        assertThat(UUID.fromString(resolved)).isNotNull();
    }

    @Test
    void anOverlongId_isReplaced() {
        String tooLong = "a".repeat(129);
        assertThat(RequestIdWebFilter.resolve(tooLong)).isNotEqualTo(tooLong);
    }
}
