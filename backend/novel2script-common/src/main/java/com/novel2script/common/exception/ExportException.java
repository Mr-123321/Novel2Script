package com.novel2script.common.exception;

/**
 * Exception thrown during script export operations (YAML, JSON, PDF, etc.).
 *
 * <p>Carries an error code for programmatic handling and a human-readable
 * message for logging / UI display.
 */
public class ExportException extends RuntimeException {

    private final String errorCode;

    /**
     * Create an export exception with a default error code.
     *
     * @param message human-readable description of the failure
     */
    public ExportException(String message) {
        super(message);
        this.errorCode = "EXPORT_ERROR";
    }

    /**
     * Create an export exception wrapping a lower-level cause.
     *
     * @param message human-readable description of the failure
     * @param cause   the underlying exception that triggered this one
     */
    public ExportException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "EXPORT_ERROR";
    }

    /**
     * Create an export exception with a specific error code.
     *
     * @param errorCode machine-readable code for programmatic handling
     * @param message   human-readable description of the failure
     */
    public ExportException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Create an export exception with error code, message, and cause.
     *
     * @param errorCode machine-readable code for programmatic handling
     * @param message   human-readable description of the failure
     * @param cause     the underlying exception that triggered this one
     */
    public ExportException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Returns the machine-readable error code.
     */
    public String getErrorCode() {
        return errorCode;
    }
}
