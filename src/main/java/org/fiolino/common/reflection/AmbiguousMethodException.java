package org.fiolino.common.reflection;

/**
 * This is thrown when there were multiple methods detected that match with same comparison rank.
 */
public class AmbiguousMethodException extends RuntimeException {

    private static final long serialVersionUID = -2883393890604050516L;

    AmbiguousMethodException(String message) {
        super(message);
    }
}
