package org.pead.common.exception;

public class DataIngestionException extends PeadBaseException {

    public DataIngestionException(String message, String correlationId) {
        super(message, correlationId, "DATA_INGESTION_ERROR");
    }

    public DataIngestionException(String message, Throwable cause, String correlationId) {
        super(message, cause, correlationId, "DATA_INGESTION_ERROR");
    }
}
