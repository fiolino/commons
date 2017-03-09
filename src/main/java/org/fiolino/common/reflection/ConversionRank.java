package org.fiolino.common.reflection;

/**
 * Identification of a type hierarchy of two.
 *
 * Created by Kuli on 6/17/2016.
 */
public enum ConversionRank {
    /**
     * It|s impossible to convert from one value to the other.
     */
    IMPOSSIBLE {
        @Override
        ConversionRank compareWithSource(ConversionRank source) {
            return IMPOSSIBLE;
        }
    },
    /**
     * The two types are in the same hierarchy and are therefor directly convertible.
     */
    IN_HIERARCHY {
        @Override
        ConversionRank compareWithSource(ConversionRank source) {
            return IMPOSSIBLE; // Only source may be IN_HIERARCHY, not the target
        }
    },
    /**
     * The types are different primitives and can be cated to each other.
     */
    EXPLICITLY_CASTABLE,
    /**
     * One of the types is a primitive and the other is its wrapper type.
     */
    WRAPPABLE,
    /**
     * The two types are the same.
     */
    IDENTICAL {
        @Override
        ConversionRank compareWithSource(ConversionRank source) {
            return source;
        }
    };

    ConversionRank compareWithSource(ConversionRank source) {
        return source == this ? this : IMPOSSIBLE;
    }
}
