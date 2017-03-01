package org.fiolino.common.reflection;

/**
 * Comparison result for different methods types.
 * <p>
 * Created by Kuli on 6/1/2016.
 */
public enum Comparison {
    /**
     * This means both the prototype and the checked type are exactly the same types.
     */
    EQUAL(true, true),

    /**
     * This means the checked type is more generic than then prototype.
     * In detail, the result of the checked type is either the same type or a subtype, and all arguments
     * are either the same type or a supertype of the prototype ones.
     * <p>
     * Example: Compare (String,int)Date with (Object,Number)Timestamp
     */
    MORE_GENERIC(true, true),

    /**
     * This means the checked type is more specific than then prototype.
     * In detail, the result of the checked type is either the same type or a supertype, and all arguments
     * are either the same type or a subtype of the prototype ones.
     * <p>
     * Example:  Compare (Object)String with (String)CharSequence
     */
    MORE_SPECIFIC(true, false),

    /**
     * The prototype and the checked type are convertable with MethodType.asType(), but not more.
     * Actually, some arguments are supertypes and other subtypes, or both arguments and return types
     * are super- or subtypes.
     * <p>
     * Example: Compare (String)String with (Object)CharSequence
     * or (Number,Number)void with (Object,float)void
     * <p>
     * This can only happen if the types have at least one argument.
     */
    CONVERTABLE(true, false),

    /**
     * Either one argument or the return type is not convertible at all.
     * <p>
     * Example: Compare ()String with ()Date.
     */
    INCOMPATIBLE(false, false),

    /**
     * The checked type has less arguments than the prototype.
     */
    LESS_ARGUMENTS(false, false),

    /**
     * The checked type has more arguments than the prototype.
     */
    MORE_ARGUMENTS(false, false);

    private final boolean isConvertible;
    private final boolean isMatching;

    Comparison(boolean isConvertible, boolean isMatching) {
        this.isConvertible = isConvertible;
        this.isMatching = isMatching;
    }

    /**
     * If this is set, then the checked type can by converted via MethodType.asType().
     */
    public boolean isConvertible() {
        return isConvertible;
    }

    /**
     * If this is true, then the checked type will always match the expected prototype without throwing
     * a ClassCastException.
     * <p>
     * This means, the types are either equal or more generic.
     * <p>
     * If this is true, then the value is compatible as well.
     */
    public boolean isMatching() {
        return isMatching;
    }
}
