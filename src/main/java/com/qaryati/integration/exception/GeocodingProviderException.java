package com.qaryati.integration.exception;

/**
 * Contains a safe failure category only. Provider response bodies and request URLs are
 * deliberately not propagated because they can contain the API key or provider details.
 */
public class GeocodingProviderException extends RuntimeException {

    public enum Reason {
        CONFIGURATION,
        RATE_LIMITED,
        TIMEOUT,
        UPSTREAM,
        MALFORMED_RESPONSE,
        NETWORK
    }

    private final Reason reason;

    public GeocodingProviderException(Reason reason) {
        this(reason, null);
    }

    public GeocodingProviderException(Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
