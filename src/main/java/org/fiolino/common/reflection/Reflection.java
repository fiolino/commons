package org.fiolino.common.reflection;

import javax.swing.plaf.SplitPaneUI;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
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
                h -> MethodHandles.filterArguments(h, h.type().parameterCount() - 1, ordinal),
                enumType.getEnumConstants().length, false);
    }

    private static Registry buildForBooleanParameter(MethodHandle target) {
        return new ParameterToIntMappingRegistry(target,
                h -> MethodHandles.explicitCastArguments(h, h.type().changeParameterType(h.type().parameterCount() - 1, boolean.class)),
                2, false);
    }

    /**
     * A Registry implementation that fetches accessor and updater from arrays of MethodHandles.
     */
    static final class ParameterToIntMappingRegistry implements Registry {
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
        ParameterToIntMappingRegistry(MethodHandle target, UnaryOperator<MethodHandle> alignHandleGetter, int maximumValue,
                                      boolean shouldCheckRange) {
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
            if (shouldCheckRange) {
                getAccessor = catchOutOfBounds(getAccessor, alignHandleGetter, maximumValue);
            }
            accessor = MethodHandles.foldArguments(MethodHandles.exactInvoker(target.type()), getAccessor);

            MethodHandle getUpdater = getFromArray.bindTo(updaters);
            getUpdater = alignHandleGetter.apply(getUpdater);
            if (shouldCheckRange) {
                getUpdater = catchOutOfBounds(getUpdater, alignHandleGetter, maximumValue);
            }
            updater = MethodHandles.foldArguments(MethodHandles.exactInvoker(target.type()), getUpdater);
        }

        private static MethodHandle catchOutOfBounds(MethodHandle target, UnaryOperator<MethodHandle> alignHandleGetter, int maximumValue) {
            MethodType targetType = target.type();
            MethodHandle newException, newStringBuilder, stringBuilderAppend, stringBuilderAppendInt, stringBuilderToString, arraysToString;
            try {
                newException = publicLookup().findConstructor(IllegalArgumentException.class, methodType(void.class, String.class, Throwable.class));
                newStringBuilder = publicLookup().findConstructor(StringBuilder.class, methodType(void.class, String.class));
                stringBuilderAppend = publicLookup().findVirtual(StringBuilder.class, "append", methodType(StringBuilder.class, String.class));
                stringBuilderAppendInt = publicLookup().findVirtual(StringBuilder.class, "append", methodType(StringBuilder.class, int.class));
                stringBuilderToString = publicLookup().findVirtual(StringBuilder.class, "toString", methodType(String.class));
                arraysToString = publicLookup().findStatic(Arrays.class, "toString", methodType(String.class, Object[].class));
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw new InternalError(ex);
            }
            newStringBuilder = newStringBuilder.bindTo("Argument value ");
            MethodHandle appendArgument = MethodHandles.collectArguments(stringBuilderAppend, 0, newStringBuilder);
            appendArgument = MethodHandles.filterArguments(appendArgument, 0, arraysToString);
            appendArgument = appendArgument.asCollector(Object[].class, targetType.parameterCount()).asType(targetType.changeReturnType(StringBuilder.class));
            appendArgument = MethodHandles.filterReturnValue(appendArgument, MethodHandles.insertArguments(stringBuilderAppend, 1, " resolves to "));
            MethodHandle appendIndex = alignHandleGetter.apply(stringBuilderAppendInt);
            appendArgument = MethodHandles.foldArguments(appendIndex, appendArgument);
            appendArgument = MethodHandles.filterReturnValue(appendArgument, MethodHandles.insertArguments(stringBuilderAppend, 1, " which is beyond 0.." + (maximumValue - 1)));

            MethodHandle parametersToString = MethodHandles.filterReturnValue(appendArgument, stringBuilderToString);
            // Switch parameters of exception constructor
            newException = MethodHandles.permuteArguments(newException, methodType(IllegalArgumentException.class, Throwable.class, String.class), 1, 0);
            newException = MethodHandles.collectArguments(newException, 1, parametersToString);

            MethodHandle throwException = MethodHandles.throwException(targetType.returnType(), IllegalArgumentException.class);
            throwException = MethodHandles.collectArguments(throwException, 0, newException);

            return MethodHandles.catchException(target, ArrayIndexOutOfBoundsException.class,
                    throwException);
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
