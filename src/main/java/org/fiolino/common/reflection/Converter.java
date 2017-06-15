package org.fiolino.common.reflection;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * This is the result from a ConverterLocator request.
 *
 * Created by kuli on 20.03.17.
 */
public final class Converter {
    private final MethodHandle converter;
    private final ConversionRank rank;
    private final Class<?> targetType;

    Converter(MethodHandle converter, ConversionRank rank, Class<?> targetType) {
        this.converter = converter;
        this.rank = rank;
        this.targetType = targetType;
    }

    Converter(ConversionRank rank, Class<?> targetType) {
        this(null, rank, targetType);
    }

    @Nullable
    public MethodHandle getConverter() {
        return converter;
    }

    public ConversionRank getRank() {
        return rank;
    }

    public Class<?> getTargetType() {
        return targetType;
    }

    public boolean isConvertable() {
        return converter != null || rank != ConversionRank.NEEDS_CONVERSION;
    }

    Converter better(Converter other, Class<?> source) {
        if (getRank() == other.getRank()) {
            if (other.getConverter() != null) {
                if (getConverter() != null) return this;
                ConversionRank convRank = ConversionRank.getRankOf(other.getConverter().type(), source, targetType);
                if (convRank.isBetterThan(getRank())) {
                    return other;
                }
                return this;
            }
            return this;
        }
        return other.getRank().isBetterThan(getRank()) ? other : this;
    }

    @Override
    public String toString() {
        String s = rank.name() + ": to " + targetType.getName();
        return converter == null ? s : (s + " using " + converter);
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this ||
                obj != null && obj.getClass().equals(Converter.class) && Objects.equals(((Converter) obj).getConverter(), getConverter())
                && ((Converter) obj).getRank() == getRank() && getTargetType().equals(((Converter) obj).getTargetType());
    }

    @Override
    public int hashCode() {
        int h = converter == null ? 0 : converter.hashCode() * 31;
        h += targetType.hashCode();
        h *= 31;
        return h + rank.hashCode();
    }

    public Converter convertWith(UnaryOperator<MethodHandle> handleChanger) {
        if (converter == null) return this;
        return new Converter(handleChanger.apply(converter), rank, targetType);
    }
}
