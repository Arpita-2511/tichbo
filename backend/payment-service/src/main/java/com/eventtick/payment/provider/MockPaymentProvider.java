package com.eventtick.payment.provider;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The only {@link PaymentProvider} implementation for Phase 15 Step 2 — no
 * network call, no credentials, deterministic, synchronous. Selected when
 * {@code payment.default-provider} is {@code MOCK} (the default — see
 * {@code application.yml}).
 *
 * <p><b>Deterministic rule:</b> succeeds for every request, <i>except</i>
 * when the idempotency key carries the literal test marker prefix
 * {@value #FORCE_FAILURE_PREFIX} — a deliberate, documented escape hatch
 * so tests can exercise the failure path without needing a second provider
 * implementation or reflection tricks. No real provider would ever key
 * its behavior off a client-supplied string this way; this is a test
 * convenience specific to the mock.
 */
@Component
public class MockPaymentProvider implements PaymentProvider {

    /** Idempotency keys starting with this literal cause a deterministic failure — see class Javadoc. */
    public static final String FORCE_FAILURE_PREFIX = "FORCE_FAIL_";

    @Override
    public String id() {
        return "MOCK";
    }

    @Override
    public PaymentProviderResult charge(PaymentChargeRequest request) {
        if (request.idempotencyKey() != null && request.idempotencyKey().startsWith(FORCE_FAILURE_PREFIX)) {
            return PaymentProviderResult.failure(null, "Mock provider: forced failure for test key " + request.idempotencyKey());
        }
        return PaymentProviderResult.success("mock_txn_" + UUID.randomUUID());
    }
}
