package org.fiolino.common.reflection;

/**
 * Created by kuli on 14.03.17.
 */
public interface Updateable extends Resettable {
    /**
     * Updates to a new return value.
     *
     * The original execution handle will not be triggered even if it never was yet.
     *
     * If the handle does not return a value, the new value is ignored completely. Otherwise it must match the target's return type.
     *
     * If there is some concurrent thread execution my target, then this methods waits until it's finished.
     *
     * @param newValue Sets this as the new result value
     */
    void updateTo(Object newValue);
}
