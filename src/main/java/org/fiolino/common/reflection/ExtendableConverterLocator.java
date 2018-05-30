package org.fiolino.common.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * A {@link ConverterLocator} where you can register individual converters for certain types.
 *
 * Created by Kuli on 6/20/2016.
 */
public abstract class ExtendableConverterLocator implements ConverterLocator {
    /**
     * A {@link ConverterLocator} that can be stacked with other ConverterLocators.
     *
     * Created by Kuli on 6/20/2016.
     */
    private static abstract class StackedConverterLocator extends ExtendableConverterLocator {
        private final ExtendableConverterLocator fallback;

        StackedConverterLocator(ExtendableConverterLocator fallback) {
            this.fallback = fallback;
        }

        @Override
        public Converter find(Class<?> source, Class<?>[] targets) {
            Converter thisConverter = super.find(source, targets);
            ConversionRank rank = thisConverter.getRank();
            if (rank == ConversionRank.IDENTICAL) {
                // Perfect
                return thisConverter;
            }
            Converter option = fallback.find(source, targets);
            return thisConverter.better(option, source);
        }

        @Override
        void printAll(StringBuilder sb) {
            print(sb);
            sb.append("; ");
            fallback.printAll(sb);
        }

        abstract void print(StringBuilder sb);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        printAll(sb);
        return sb.toString();
    }

    abstract void printAll(StringBuilder sb);

    @Override
    public Converter find(Class<?> source, Class<?>... targets) {
        if (targets.length == 0) {
            throw new IllegalArgumentException("Expected at least one target");
        }
        Class<?> bestType = targets[0];
        ConversionRank bestRank = ConversionRank.getFor(bestType, source);
        for (int i=1; i < targets.length; i++) {
            Class<?> t = targets[i];
            ConversionRank r = ConversionRank.getFor(t, source);
            if (r.isBetterThan(bestRank)) {
                bestRank = r;
                bestType = t;
            }
        }
        MethodHandle handle = getConverter(source, bestType);

        return new Converter(handle, bestRank, bestType);
    }

    abstract MethodHandle getConverter(Class<?> source, Class<?> target);

    /**
     * A ConverterLocator that does not contain any converters at all.
     */
    public static final ExtendableConverterLocator EMPTY = new ExtendableConverterLocator() {
        @Override
        MethodHandle getConverter(Class<?> source, Class<?> target) {
            return null;
        }

        @Override
        void printAll(StringBuilder sb) {
            sb.append("no more.");
        }
    };

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
        protected MethodHandle getConverter(Class<?> source, Class<?> target) {
            MethodType t = handle.type();
            if (!t.returnType().equals(target)) {
                return null;
            }
            if (t.parameterType(0).isAssignableFrom(source))
                return handle;
            return null;
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
        return MethodLocator.visitMethodsWithStaticContext(lookup, converterMethods, this,
                (loc, l, m, handleSupplier) -> {

                    if (!m.isAnnotationPresent(ConvertValue.class)) {
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
