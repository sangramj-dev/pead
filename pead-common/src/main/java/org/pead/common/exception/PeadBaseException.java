package org.pead.common.exception;

/**
 * Base exception for all PEAD platform exceptions.
 * Provides correlation ID tracking for distributed tracing.
 */
public class PeadBaseException extends RuntimeException {

    private final String correlationId;
    private final String errorCode;

    public PeadBaseException(String message, String correlationId, String errorCode) {
        super(message);
        this.correlationId = correlationId;
        this.errorCode = errorCode;
    }

    public PeadBaseException(String message, Throwable cause, String correlationId, String errorCode) {
        super(message, cause);
        this.correlationId = correlationId;
        this.errorCode = errorCode;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
