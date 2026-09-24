package com.eventtick.gateway.ratelimit;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCategoryClassifierTest {

    @ParameterizedTest
    @CsvSource({
            "/api/auth/login, AUTH",
            "/api/auth/register, AUTH",
            "/api/auth/refresh, AUTH",
            "/api/catalog/content, CATALOG",
            "/api/catalog/shows/123, CATALOG",
            "/api/bookings, BOOKING",
            "/api/bookings/shows/1/seats, BOOKING",
            "/api/users/me, USER",
            "/api/users, USER",
            "/api/unknown, UNKNOWN",
            "/actuator/health, UNKNOWN",
            "/, UNKNOWN",
    })
    void classifiesByPathPrefix(String path, RequestCategory expected) {
        assertThat(RequestCategoryClassifier.classify(path)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "/api/auth, UNKNOWN",   // no trailing content after the prefix, not just the bare segment
            "/api/authx/login, UNKNOWN", // must not match on a mere string prefix of the segment
            "/api/catalogx, UNKNOWN",
    })
    void doesNotMatchOnAPartialPathSegment(String path, RequestCategory expected) {
        assertThat(RequestCategoryClassifier.classify(path)).isEqualTo(expected);
    }
}
