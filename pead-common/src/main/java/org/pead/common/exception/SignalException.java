package org.pead.common.exception;

public class SignalException extends PeadBaseException {

    public SignalException(String message, String correlationId) {
        super(message, correlationId, "SIGNAL_ERROR");
    }

    public SignalException(String message, Throwable cause, String correlationId) {
        super(message, cause, correlationId, "SIGNAL_ERROR");
    }
}
