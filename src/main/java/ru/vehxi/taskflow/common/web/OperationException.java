package ru.vehxi.taskflow.common.web;

/** An expected operation failure with a safe client-facing message. */
public class OperationException extends RuntimeException {
    /** HTTP response status. */
    private final int status;
    /** Stable client error code. */
    private final String code;

    /** Initializes the component with the supplied arguments.
     * @param status HTTP status
     * @param code stable application code
     * @param message safe message */
    public OperationException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    /** Returns HTTP status.
     * @return HTTP status */
    public int getStatus() { return status; }
    /** Returns stable application code.
     * @return stable application code */
    public String getCode() { return code; }
}
