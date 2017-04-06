package org.fiolino.common.util;

/**
 * Prints a serialized form of some value into a StringBuilder.
 *
 * Created by kuli on 29.12.15.
 */
interface SerialPresenter<T> {
    /**
     * Prints the given value into the StringBuilder.
     *
     * @param sb The target
     * @param value The value
     */
    void printInto(StringBuilder sb, T value);

    /**
     * Prints a description of the presenter, used for debug output.
     *
     * @param sb The target
     */
    void printDescription(StringBuilder sb);
}
