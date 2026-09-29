package com.eventtick.payment.provider;

/**
 * Abstraction over "however a charge actually gets attempted" — see
 * docs/architecture.md §25.1. {@link MockPaymentProvider} is the only
 * implementation for now; a real provider (Stripe/Razorpay/etc.) would
 * implement this same interface later, selected via
 * {@code payment.default-provider} configuration, without any change to
 * {@code com.eventtick.payment.service.PaymentService}. Deliberately kept
 * synchronous even though a real provider might resolve asynchronously
 * (a webhook) — the payment state model's own {@code PENDING} state
 * already accommodates that gap; only a future implementation's shape
 * would need to change, not this interface or the state model.
 */
public interface PaymentProvider {

    /** A short, stable identifier for this provider, e.g. "MOCK" — stored on {@code payments.provider}. */
    String id();

    PaymentProviderResult charge(PaymentChargeRequest request);
}
