package org.apache.alock.exception;

/**
 * 乐观锁定义
 * @author wy
 */
public class OptimisticLockingException extends RuntimeException{
    private static final long serialVersionUID = 1L;

    /**
     * Default constructor
     */
    public OptimisticLockingException() {
        super();
    }

    /**
     * Constructor with message & cause
     *
     * @param message
     * @param cause
     */
    public OptimisticLockingException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructor with message
     *
     * @param message
     */
    public OptimisticLockingException(String message) {
        super(message);
    }
}
