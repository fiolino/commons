package org.fiolino.common.reflection;

/**
 * Used to create a copy of an existing array with a new length.
 *
 * Like Arrays.opyOf(), but with variable array types (does allow primitive arrays).
 *
 * @param <A> The array type - int[], String[], Object[][], or whatever
 */
@FunctionalInterface
public interface ArrayCopier<A> {

    /**
     * Calls Arrays.copyOf() with the given argument.
     * Will fail if original is not of the accepted type.
     */
    A copyOf(A original, int newLength);
}
