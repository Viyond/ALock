package org.apache.alock.exception;

/**
 * 异常定义
 * @author wy
 */
public class ALockProcessException extends RuntimeException{

    private static final long serialVersionUID = 1L;

    /**
     * Default constructor
     */
    public ALockProcessException() {
        super();
    }

    /**
     * Constructor with message & cause
     * @param message
     * @param cause
     */
    public ALockProcessException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructor with message
     * @param message
     */
    public ALockProcessException(String message) {
        super(message);
    }

    /**
     * Constructor with cause
     * @param cause
     */
    public ALockProcessException(Throwable cause) {
        super(cause);
    }
}
