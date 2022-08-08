package org.apache.alock.exception;

/**
 * 异常定义
 * @author wy
 */
public class RedisProcessException extends ALockProcessException{
    private static final long serialVersionUID = 1L;

    /**
     * Default constructor
     */
    public RedisProcessException() {
        super();
    }

    /**
     * Constructor with message & cause
     * @param message
     * @param cause
     */
    public RedisProcessException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructor with message
     * @param message
     */
    public RedisProcessException(String message) {
        super(message);
    }

    /**
     * Constructor with cause
     * @param cause
     */
    public RedisProcessException(Throwable cause) {
        super(cause);
    }
}
