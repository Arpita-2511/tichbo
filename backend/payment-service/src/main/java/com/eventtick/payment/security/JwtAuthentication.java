package com.eventtick.payment.security;

import java.util.UUID;

/** The claims extracted from a successfully validated JWT. Mirrors user-service's own record. */
public record JwtAuthentication(UUID userId, PaymentRole role) {
}
