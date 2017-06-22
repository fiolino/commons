package org.fiolino.common.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.function.UnaryOperator;

import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Created by Kuli on 6/17/2016.
 */
final class Reflection {
    /**
     * Builds a Registry for a given target handle.
     *
     * Implementation note:
     * This works for all kinds of MethodHandles if the resulting handle has no parameters,
     * but it will only work for direct method handles if there are some parameter types.
     *
     * @param target This handle will be called only once per parameter value.
     * @param leadingParameters Some values that are being added to the target at first
     * @return A Registry holding handles with exactly the same type as the target type minus the leading parameters
     */
    static Registry buildFor(MethodHandle target, Object... leadingParameters) {
        int pCount = target.type().parameterCount() - leadingParameters.length;
        if (pCount < 0) {
            throw new IllegalArgumentException("Too many leading parameters for " + target);
        }
        switch (pCount) {
            case 0:
                // No args, so just one simple instance
                target = insertLeadingArguments(target, leadingParameters);
                return new OneTimeRegistryBuilder(target, false);
            case 1:
                // Maybe just some type with few possible values?
                Class<?> singleArgumentType = target.type().parameterType(leadingParameters.length);
                if (singleArgumentType.isEnum() && singleArgumentType.getEnumConstants().length <= 64) {
                    // Then it can be mapped directly
                    target = insertLeadingArguments(target, leadingParameters);
                    return buildForEnumParameter(target, singleArgumentType.asSubclass(Enum.class));
                }
                if (singleArgumentType == boolean.class) {
                    target = insertLeadingArguments(target, leadingParameters);
                    return buildForBooleanParameter(target);
                }
            default:
                return MultiArgumentExecutionBuilder.createFor(target, leadingParameters);
        }
    }

    private static MethodHandle insertLeadingArguments(MethodHandle target, Object[] leadingParameters) {
        if (leadingParameters.length > 0) {
            target = MethodHandles.insertArguments(target, 0, leadingParameters);
        }
        return target;
    }

    private static <E extends Enum<E>> Registry buildForEnumParameter(MethodHandle target, Class<E> enumType) {
        MethodHandle ordinal;
        try {
            ordinal = publicLookup().findVirtual(enumType, "ordinal", methodType(int.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new InternalError("ordinal", ex);
        }
        return new ParameterToIntMappingRegistry(target,
                h -> MethodHandles.filterArguments(h, 0, ordinal), enumType.getEnumConstants().length);
    }

    private static Registry buildForBooleanParameter(MethodHandle target) {
        return new ParameterToIntMappingRegistry(target,
                h -> MethodHandles.explicitCastArguments(h, methodType(MethodHandle.class, boolean.class)), 2);
    }

    /**
     * A Registry implementation that fetches accessor and updater from arrays of MethodHandles.
     */
    private static final class ParameterToIntMappingRegistry implements Registry {
        private final Resettable[] resettables;
        private final MethodHandle accessor, updater;

        /**
         * Creates the special registry.
         *
         * @param target Use this as the called handle.
         * @param alignHandleGetter Transforms the handle that fetches another handle from an array by its int position
         *                          to something fetching by the target's parameter value; more detailed, it transforms
         *                          (int)MethodHandle into (0..n-1)MethodHandle where 0..n-1 are target's first n parameters.
         * @param maximumValue The maximum size of the used arrays
         */
        ParameterToIntMappingRegistry(MethodHandle target, UnaryOperator<MethodHandle> alignHandleGetter, int maximumValue) {
            Registry[] registries = new Registry[maximumValue];
            MethodHandle[] accessors = new MethodHandle[maximumValue];
            MethodHandle[] updaters = new MethodHandle[maximumValue];
            for (int i=0; i < maximumValue; i++) {
                Registry r = OneTimeExecution.createFor(target);
                registries[i] = r;
                accessors[i] = r.getAccessor();
                updaters[i] = r.getUpdater();
            }
            resettables = registries;

            MethodHandle getFromArray = MethodHandles.arrayElementGetter(MethodHandle[].class);
            MethodHandle getAccessor = getFromArray.bindTo(accessors);
            getAccessor = alignHandleGetter.apply(getAccessor);
            accessor = MethodHandles.foldArguments(MethodHandles.exactInvoker(target.type()), getAccessor);

            MethodHandle getUpdater = getFromArray.bindTo(updaters);
            getUpdater = alignHandleGetter.apply(getUpdater);
            updater = MethodHandles.foldArguments(MethodHandles.exactInvoker(target.type()), getUpdater);
        }

        @Override
        public void reset() {
            for (Resettable r : resettables) {
                r.reset();
            }
        }

        @Override
        public MethodHandle getAccessor() {
            return accessor;
        }

        @Override
        public MethodHandle getUpdater() {
            return updater;
        }
    }
}
