package org.fiolino.common.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Created by Kuli on 6/20/2016.
 */
public abstract class ExtendableConverterLocator implements ConverterLocator {
    private static abstract class StackedConverterLocator extends ExtendableConverterLocator {
        private final ExtendableConverterLocator fallback;

        StackedConverterLocator(ExtendableConverterLocator fallback) {
            this.fallback = fallback;
        }

        @Override
        MethodHandle find(Class<?> source, Class<?> target, ConversionRank maxRank) {
            MethodHandle handle = getConverter(source, target);
            if (handle == null) {
                return fallback.find(source, target, maxRank);
            }
            ConversionRank rank = getRankOf(handle.type(), source, target);
            if (rank == ConversionRank.IDENTICAL) {
                // Perfect
                return handle;
            }
            if (rank.compareTo(maxRank) < 0) {
                // Too bad
                return fallback.find(source, target, maxRank);
            }
            MethodHandle option = fallback.find(source, target, rank);
            if (option != null) {
                // We found another converter
                ConversionRank optionRank = getRankOf(option.type(), source, target);
                if (rank == optionRank) {
                    // Multiple best matching
                    if (rank != ConversionRank.WRAPPABLE) {
                        // Otherwise it's okay
                        throw new NoMatchingConverterException("Multiple non-perfect converters from " + source.getName()
                                + " to " + target.getName() + " found, e.g. " + handle + " and " + option);
                    }
                } else if (rank.compareTo(optionRank) < 0) {
                    // This one is better
                    return option;
                }
            }
            // Mine is better
            if (Converters.compare(target, source) != ConversionRank.IMPOSSIBLE) {
                // Better direct than with a non-perfect converter
                return null;
            }
            return handle;
        }

        @Override
        void printAll(StringBuilder sb) {
            print(sb);
            sb.append("; ");
            fallback.printAll(sb);
        }

        abstract void print(StringBuilder sb);

        abstract MethodHandle getConverter(Class<?> source, Class<?> target);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        printAll(sb);
        return sb.toString();
    }

    abstract void printAll(StringBuilder sb);

    abstract MethodHandle find(Class<?> source, Class<?> target, ConversionRank maxRank);

    @Override
    public final MethodHandle find(Class<?> source, Class<?> target) {
        return find(source, target, ConversionRank.IN_HIERARCHY);
    }

    /**
     * A ConverterLocator that does not contain any converters at all.
     */
    public static final ExtendableConverterLocator EMPTY = new ExtendableConverterLocator() {
        @Override
        MethodHandle find(Class<?> source, Class<?> target, ConversionRank maxRank) {
            return null;
        }

        @Override
        void printAll(StringBuilder sb) {
            sb.append("no more.");
        }
    };

    /**
     * Gets the conversion rank of a type regarding the expected parameters.
     * Only the source may be iN_HIERARCHY.
     *
     * @param type   The type to check
     * @param source From here
     * @param target to there
     * @return The common (worst) rank
     */
    private static ConversionRank getRankOf(MethodType type, Class<?> source, Class<?> target) {
        ConversionRank sourceRank = Converters.compare(type.parameterType(0), source);
        ConversionRank targetRank = Converters.compare(target, type.returnType());
        if (sourceRank == targetRank) {
            return sourceRank;
        }
        if (sourceRank == ConversionRank.IDENTICAL) {
            return targetRank;
        }
        if (targetRank == ConversionRank.IDENTICAL) {
            return sourceRank;
        }
        return ConversionRank.IMPOSSIBLE;
    }

    private static class DirectMethodHandleContainer extends StackedConverterLocator {
        private final MethodHandle handle;

        DirectMethodHandleContainer(ExtendableConverterLocator fallback, MethodHandle handle) {
            super(fallback);
            this.handle = handle;
        }

        @Override
        void print(StringBuilder sb) {
            MethodType type = handle.type();
            sb.append(type.parameterType(0).getName()).append(" -> ").append(type.returnType().getName());
        }

        @Override
        protected java.lang.invoke.MethodHandle getConverter(Class<?> source, Class<?> target) {
            if (getRankOf(handle.type(), source, target) == ConversionRank.IMPOSSIBLE) {
                return null;
            }
            return handle;
        }
    }

    private static class MethodHandleFactory extends StackedConverterLocator {
        private final String name;
        private final MethodHandle factoryHandle;

        MethodHandleFactory(ExtendableConverterLocator fallback, String name, MethodHandle factoryHandle) {
            super(fallback);
            this.name = name;
            MethodType type = factoryHandle.type();
            if (type.parameterCount() != 2 || type.parameterType(0) != Class.class || type.parameterType(1) != Class.class) {
                throw new IllegalArgumentException(factoryHandle + " must accept a source and a target type!");
            }
            if (type.returnType() != MethodHandle.class) {
                throw new IllegalArgumentException(factoryHandle + " must return a handle!");
            }
            this.factoryHandle = factoryHandle;
        }

        @Override
        void print(StringBuilder sb) {
            sb.append("Factory: ").append(name);
        }

        @Override
        protected MethodHandle getConverter(Class<?> source, Class<?> target) {
            try {
                return (MethodHandle) factoryHandle.invokeExact(source, target);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

    /**
     * Register a MethodHandle that accepts some value and returns another.
     *
     * @param handle One-argument handle for conversion
     * @return A converterLocator that handles this
     */
    public ExtendableConverterLocator register(MethodHandle handle) {
        MethodType type = handle.type();
        if (type.parameterCount() != 1) {
            throw new IllegalArgumentException(handle + " must accept exactly one argument!");
        }
        if (type.returnType() == void.class) {
            throw new IllegalArgumentException(handle + " must return a value!");
        }
        if (type.returnType().equals(type.parameterType(0))) {
            throw new IllegalArgumentException(handle + " must return a different type than it accepts!");
        }
        return new DirectMethodHandleContainer(this, handle);
    }

    /**
     * Registers all converters from the given converter method instance.
     * The given parameter is analyzed and scanned for all methods annotated with @{@link Converter}. That method is used as a converter method then.
     *
     * Converter method means it can either be a simple converter, that is some method that accepts one value and returns the converted counterpart.
     *
     * Or it can be a method returning some MethodHandle. If that method is paremeterless, then it's being executed immediately, using
     * the returned MethodHandle as a converter as if registered directly.
     *
     * Or that method accepts exactly two {@link Class} instances. In that case it's being called dynamically when someone is asking for a converter of
     * a given type. The two parameters will be the source and the target type then.
     *
     * @param converterMethods Some instance or class; evaluation syntax is as in Methods.visitMethodsWithStaticContext().
     * @return A new locator with the registered converters
     */
    public ExtendableConverterLocator register(Object converterMethods) {
        return register(MethodHandles.publicLookup().in(converterMethods.getClass()), converterMethods);
    }

    /**
     * Registers all converters from the given converter method instance.
     * The given parameter is analyzed and scanned for all methods annotated with @{@link Converter}. That method is used as a converter method then.
     *
     * Converter method means it can either be a simple converter, that is some method that accepts one value and returns the converted counterpart.
     *
     * Or it can be a method returning some MethodHandle. If that method is paremeterless, then it's being executed immediately, using
     * the returned MethodHandle as a converter as if registered directly.
     *
     * Or that method accepts exactly two {@link Class} instances. In that case it's being called dynamically when someone is asking for a converter of
     * a given type. The two parameters will be the source and the target type then.
     *
     * @param lookup The local lookup instance which can access the marked methods in the converter parameter
     * @param converterMethods Some instance or class; evaluation syntax is as in Methods.visitMethodsWithStaticContext().
     * @return A new locator with the registered converters
     */
    public ExtendableConverterLocator register(MethodHandles.Lookup lookup, Object converterMethods) {
        return Methods.visitMethodsWithStaticContext(lookup, converterMethods, this,
                (loc, m, handleSupplier) -> {

                    if (!m.isAnnotationPresent(Converter.class)) {
                        return loc;
                    }
                    Class<?>[] parameterTypes = m.getParameterTypes();
                    if (m.getReturnType() == MethodHandle.class) {
                        switch (parameterTypes.length) {
                            case 0:
                                MethodHandle h;
                                try {
                                    h = (MethodHandle) handleSupplier.get().invokeExact();
                                } catch (RuntimeException | Error e) {
                                    throw e;
                                } catch (Throwable t) {
                                    throw new RuntimeException(t);
                                }
                                if (h == null) {
                                    return loc;
                                }
                                return loc.register(h);
                            case 1:
                                // Then it's a normal converter to some MethodHandle - whyever
                                break;
                            case 2:
                                if (parameterTypes[0] == Class.class && parameterTypes[1] == Class.class) {
                                    return new MethodHandleFactory(loc, m.getName(), handleSupplier.get());
                                }
                            default:
                                throw new AssertionError(m + " is expected to accept a source and a target type");
                        }
                    }
                    if (parameterTypes.length != 1 || m.getReturnType() == void.class || m.getReturnType().equals(parameterTypes[0])) {
                        throw new AssertionError(m + " should convert from one value to another.");
                    }
                    return loc.register(handleSupplier.get());
                });
    }
}
