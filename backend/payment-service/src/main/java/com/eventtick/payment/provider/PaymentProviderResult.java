package com.eventtick.payment.provider;

/**
 * The outcome of a {@link PaymentProvider#charge} attempt. {@code
 * providerReference} is the provider's own transaction id (set on both
 * success and failure where the provider assigns one; may be {@code null}
 * for a failure that never reached the provider, e.g. a validation
 * rejection). {@code failureReason} is {@code null} on success.
 */
public record PaymentProviderResult(boolean success, String providerReference, String failureReason) {

    public static PaymentProviderResult success(String providerReference) {
        return new PaymentProviderResult(true, providerReference, null);
    }

    public static PaymentProviderResult failure(String providerReference, String failureReason) {
        return new PaymentProviderResult(false, providerReference, failureReason);
    }
}
