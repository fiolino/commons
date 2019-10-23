package org.fiolino.common.ioc;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Optional;

import static java.lang.invoke.MethodHandles.Lookup;

public interface MethodHandleRegistry {

    /**
     * Call this to register some method handle for the FactoryFinder.
     *
     * @param handle This handle will be used as a factoy, as long as the type is compatible
     * @param initializers These will be bound as the first arguments to the registered handle
     * @throws MismatchedMethodTypeException If the resulting handle's type is not compatible with the requested one
     */
    void register(MethodHandle handle, Object... initializers) throws MismatchedMethodTypeException;

    /**
     * This lookup was given by the caller, or it's the registered lookup object from the FactoryFinder.
     */
    Lookup lookup();

    /**
     * The requested type. The same as the one given in the MethodHandleProvider::register callback.
     */
    MethodType type();

    /**
     * Finds the existing MethodHandle from the current FactoryFinder for the requested type.
     * Will ask all registered providers except the calling ones.
     */
    Optional<MethodHandle> findExisting();

    /**
     * Finds the existing MethodHandle from the current FactoryFinder for the given type.
     * Will ask all registered providers except the calling ones.
     */
    Optional<MethodHandle> findExisting(MethodType methodType);

    /**
     * Shortcut method.
     *
     * Finds the virtual method from the registered lookup whose resulting type is compatible to the requested one.
     * So the requested type must have at least one parameter; the first one must be compatible to the refc parameter.
     *
     * If there is such a handle, then it will be registered.
     *
     * @param refc The reference class
     * @param name The name of the method
     */
    default void findVirtual(Class<?> refc, String name) throws NoSuchMethodException, IllegalAccessException, MismatchedMethodTypeException {
        MethodType t = type();
        if (t.parameterCount() == 0 || !refc.isAssignableFrom(t.parameterType(0))) {
            throw new MismatchedMethodTypeException(refc.getName() + " does not match first parameter of " + t);
        }
        t = t.dropParameterTypes(0, 1);
        register(lookup().findVirtual(refc, name, t));
    }

    /**
     * Shortcut method.
     *
     * Finds the virtual method from the registered lookup whose resulting type is compatible to the requested one.
     * So the requested type must have at least one parameter; the first one must be compatible to the refc parameter.
     *
     * If there is such a handle, then it will be registered.
     *
     * @param name The name of the method
     */
    default void findVirtual(String name) throws NoSuchMethodException, IllegalAccessException, MismatchedMethodTypeException {
        MethodType t = type();
        if (t.parameterCount() == 0) {
            throw new MismatchedMethodTypeException(t + " needs at least one parameter");
        }
        Class<?> refc = t.parameterType(0);
        t = t.dropParameterTypes(0, 1);
        register(lookup().findVirtual(refc, name, t));
    }

    /**
     * Shortcut method.
     *
     * Finds the static method from the registered lookup whose type is the requested one.
     *
     * If there is such a handle, then it will be registered.
     *
     * @param refc The reference class
     * @param name The name of the method
     */
    default void findStatic(Class<?> refc, String name) throws NoSuchMethodException, IllegalAccessException, MismatchedMethodTypeException {
        register(lookup().findStatic(refc, name, type()));
    }

    /**
     * Shortcut method.
     *
     * Finds the virtual method from the registered lookup whose type is the requested one.
     * The receiver instance is then bound to the first argument, i.e. the instance itself.
     *
     * If there is such a handle, then it will be registered.
     *
     * @param receiver The instance where the method will be called on
     * @param name The name of the method
     */
    default void bind(Object receiver, String name) throws NoSuchMethodException, IllegalAccessException, MismatchedMethodTypeException {
        register(lookup().findVirtual(receiver.getClass(), name, type()), receiver);
    }
}
