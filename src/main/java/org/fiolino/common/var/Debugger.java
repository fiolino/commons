package org.fiolino.common.var;

/**
 * Utility to print out instances together with all their internal content.
 */
public class Debugger {

    /**
     * Returns an indented representation of the given object, containing all internal fields.
     *
     * @param o The inspected object
     * @return a (possibly multilined) String with the introspection of the object
     */
    public static String fullPrint(Object o) {
        return fullPrint(o, new StringBuilder()).toString();
    }

    /**
     * Returns an indented representation of the given object, containing all internal fields.
     *
     * @param o     The inspected object
     * @param depth The introspection depth
     * @return a (possibly multilined) String with the introspection of the object
     */
    public static String fullPrint(Object o, int depth) {
        return fullPrint(o, new StringBuilder(), depth).toString();
    }

    /**
     * Returns an indented representation of the given object, containing all internal fields.
     *
     * @param o         The inspected object
     * @param depth     The introspection depth
     * @param maxLength How many elements of arrays, collections, and maps are shown at max
     * @return a (possibly multilined) String with the introspection of the object
     */
    public static String fullPrint(Object o, int depth, int maxLength) {
        return fullPrint(o, new StringBuilder(), depth, maxLength).toString();
    }

    /**
     * Adds an indented representation of the given object, containing all internal fields, into a {@link StringBuilder}
     *
     * @param o  The inspected object
     * @param sb Where to add
     * @return The given StringBuilder instance itself
     */
    public static StringBuilder fullPrint(Object o, StringBuilder sb) {
        return fullPrint(o, sb, Integer.MAX_VALUE);
    }

    /**
     * Adds an indented representation of the given object, containing all internal fields, into a {@link StringBuilder}
     *
     * @param o     The inspected object
     * @param sb    Where to add
     * @param depth The introspection depth
     * @return The given StringBuilder instance itself
     */
    public static StringBuilder fullPrint(Object o, StringBuilder sb, int depth) {
        return fullPrint(o, sb, depth, 20);
    }

    /**
     * Adds an indented representation of the given object, containing all internal fields, into a {@link StringBuilder}
     *
     * @param o         The inspected object
     * @param sb        Where to add
     * @param depth     The introspection depth
     * @param maxLength How many elements of arrays, collections, and maps are shown at max
     * @return The given StringBuilder instance itself
     */
    public static StringBuilder fullPrint(Object o, StringBuilder sb, int depth, int maxLength) {
        return new ObjectToStringConverter(depth, maxLength).append(sb, o);
    }
}
