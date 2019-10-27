package org.fiolino.common.util;

/**
 * This is thrown if two types are not assignable by each other where it should be.
 */
public class NotAssignableException extends RuntimeException {
    private static final long serialVersionUID = -1512289958073742778L;

    NotAssignableException(String message) {
        super(message);
    }
}
