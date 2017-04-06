package org.fiolino.common.reflection;

import org.fiolino.common.util.Types;

import java.lang.invoke.MethodType;

/**
 * Identification of a type hierarchy of two.
 *
 * Created by Kuli on 6/17/2016.
 */
public enum ConversionRank {
    /**
     * It's impossible to convert from one value to the other.
     */
    NEEDS_CONVERSION(false) {
        @Override
        boolean isBetterThan(ConversionRank r) {
            return false;
        }
    },
    /**
     * The two types are in the same hierarchy and are therefor directly convertible.
     */
    IN_HIERARCHY(true) {
        @Override
        boolean isBetterThan(ConversionRank r) {
            return r == NEEDS_CONVERSION;
        }
    },
    /**
     * The types are different primitives and can be casted to each other.
     */
    EXPLICITLY_CASTABLE(false) {
        @Override
        boolean isBetterThan(ConversionRank r) {
            return r == NEEDS_CONVERSION;
        }
    },
    /**
     * One of the types is a primitive and the other is its wrapper type.
     */
    WRAPPABLE(false) {
        @Override
        boolean isBetterThan(ConversionRank r) {
            return r == NEEDS_CONVERSION;
        }
    },
    /**
     * The two types are the same.
     */
    IDENTICAL(true) {
        @Override
        boolean isBetterThan(ConversionRank r) {
            return r != IDENTICAL;
        }
    };

    private final boolean isEligibleForConversion;

    ConversionRank(boolean isEligibleForConversion) {
        this.isEligibleForConversion = isEligibleForConversion;
    }

    boolean isEligibleForConversion() {
        return isEligibleForConversion;
    }

    abstract boolean isBetterThan(ConversionRank r);

    /**
     * Compares a possible superclass and a possible subclass in which way they are related.
     *
     * @see ConversionRank
     *
     * @param generic The types that might be more generic
     * @param specific The type that might be more specific
     * @return The resulting rank
     */
    public static ConversionRank getFor(Class<?> generic, Class<?> specific) {
        if (generic == void.class || specific == void.class) {
            return ConversionRank.NEEDS_CONVERSION;
        }
        if (generic.equals(specific)) {
            return ConversionRank.IDENTICAL;
        }
        if (generic.isPrimitive()) {
            if (specific.isPrimitive()) {
                return ConversionRank.EXPLICITLY_CASTABLE;
            }
            if (Types.asPrimitive(specific) == generic) {
                return ConversionRank.WRAPPABLE;
            }
        } else if (specific.isPrimitive() && Types.asPrimitive(generic) == specific) {
            return ConversionRank.WRAPPABLE;
        }

        if (Types.isAssignableFrom(generic, specific)) {
            return ConversionRank.IN_HIERARCHY;
        }
        return ConversionRank.NEEDS_CONVERSION;
    }

    /**
     * Gets the conversion rank of a type regarding the expected parameters.
     * Only the source may be iN_HIERARCHY.
     *
     * @param type   The type to check
     * @param source From here
     * @param target to there
     * @return The common (worst) rank
     */
    static ConversionRank getRankOf(MethodType type, Class<?> source, Class<?> target) {
        ConversionRank sourceRank = getFor(type.parameterType(0), source);
        ConversionRank targetRank = getFor(target, type.returnType());
        if (sourceRank == targetRank) {
            return sourceRank;
        }
        if (sourceRank == ConversionRank.IDENTICAL) {
            return targetRank;
        }
        if (targetRank == ConversionRank.IDENTICAL) {
            return sourceRank;
        }
        return ConversionRank.NEEDS_CONVERSION;
    }


}
