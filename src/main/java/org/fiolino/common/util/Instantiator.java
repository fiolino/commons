package org.fiolino.common.util;

import org.fiolino.common.ioc.PostProcessor;
import org.fiolino.data.annotation.Mandatory;

import java.lang.annotation.Annotation;
import java.lang.invoke.*;
import java.lang.reflect.Constructor;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Creates a {@link Supplier} or {@link Function} to instantiate objects.
 *
 * Created by kuli on 10.02.15.
 */
public abstract class Instantiator<T> {

  final MethodHandle constructor;
  final Class<T> type;

  private Instantiator(Class<T> type, MethodHandle constructor) {
    this.type = type;
    this.constructor = constructor;
  }

  /**
   * Static methods to directly instantiate a type.
   * Esp. good for a one-time usage.
   *
   * @param type The type to instantiate; needs an empty public constructor
   * @param <T> The type
   * @return The newly created instance
   */
  public static <T> T instantiate(Class<T> type) {
    return instantiate(publicLookup().in(type), type);
  }

  /**
   * Static methods to directly instantiate a type.
   * Esp. good for a one-time usage.
   *
   * @param lookup Use this to find the empty constructor
   * @param type The type to instantiate; needs an empty constructor visible to the given Lookup
   * @param <T> The type
   * @return The newly created instance
   */
  public static <T> T instantiate(MethodHandles.Lookup lookup, Class<T> type) {
    MethodHandle handle = findConstructor(lookup, type);
    return createInstance(type, handle);
  }

  /**
   * Creates a {@link Supplier} that will return a new instance on every call.
   *
   * @param type The type to instantiate; needs an empty public constructor
   * @param <T> The type
   * @return The Supplier
   */
  public static <T> Supplier<T> creatorFor(Class<T> type) {
    return creatorFor(publicLookup().in(type), type);
  }

  /**
   * Creates a {@link Function} that will return a new instance on every call.
   *
   * @param type The type to instantiate; needs a public constructor with exactly one argument
   *               of type parameterType, or an empty one as an alternative
   * @param <T> The type
   * @return The Function that accepts the only parameter and returns a freshly intantiated object
   */
  public static <T, P> Function<P, T> creatorWithArgument(Class<T> type, Class<P> parameterType) {
    return creatorWithArgument(publicLookup().in(type), type, parameterType);
  }

  /**
   * Creates a {@link Supplier} that will return a new instance on every call.
   *
   * @param lookup Use this to find the empty constructor
   * @param type The type to instantiate; needs an empty constructor visible to the given Lookup
   * @param <T> The type
   * @return The Supplier
   */
  public static <T> Supplier<T> creatorFor(MethodHandles.Lookup lookup, Class<T> type) {
    MethodHandle constructor = findConstructorWithExactReturnType(lookup, type);
    Supplier<T> supplier = createSupplierOf(constructor, type);
    if (supplier == null) {
      return new EmptyConstructorInstantiator<>(type, constructor);
    } else {
      return supplier;
    }
  }

  /**
   * Creates a {@link Function} that will return a new instance on every call.
   *
   * @param lookup Use this to find the constructor that must be either empty or accept exactly one argument
   *               of type parameterType; if both are available, the latter one is favored,
   * @param type The type to instantiate; needs a constructor visible to the given Lookup with exactly one argument
   *               of type parameterType, or an empty one as an alternative
   * @param <T> The type
   * @return The Function that accepts the only parameter and returns a freshly intantiated object
   */
  public static <T, P> Function<P, T> creatorWithArgument(MethodHandles.Lookup lookup, Class<T> type, Class<P> parameterType) {
    MethodHandle constructor = findConstructor(lookup, type, parameterType);
    Function<P, T> function = createFunctionOf(constructor, type, parameterType);
    if (function == null) {
      return new ArgumentInstantiator<>(type, constructor);
    } else {
      return function;
    }
  }

  /**
   * Gets the underlying type.
   */
  public final Class<T> getType() {
    return type;
  }

  private static <T> Supplier<T> createSupplierOf(MethodHandle handle, Class<T> returnType) {
    if (isPostProcessor(returnType)) {
      return null;
    }
    CallSite callSite;
    try {
      callSite = LambdaMetafactory.metafactory(MethodHandles.lookup(), "get",
              methodType(Supplier.class), methodType(Object.class),
              handle, methodType(returnType));
    } catch (LambdaConversionException | IllegalArgumentException ex) {
      // Then it's not a direct handle
      return null;
    }
    try {
      @SuppressWarnings("unchecked")
      Supplier<T> supplier = (Supplier<T>) callSite.getTarget().invokeExact();
      return supplier;
    } catch (Throwable ex) {
      throw new AssertionError("Cannot convert to Supplier", ex);
    }
  }

  private static <T, P> Function<P, T> createFunctionOf(MethodHandle handle, Class<T> returnType, Class<P> argumentType) {
    if (isPostProcessor(returnType)) {
      return null;
    }
    CallSite callSite;
    try {
      callSite = LambdaMetafactory.metafactory(MethodHandles.lookup(), "apply", methodType(Function.class),
              methodType(Object.class, Object.class), handle, methodType(returnType, argumentType));
    } catch (LambdaConversionException | IllegalArgumentException ex) {
      // Then it's not a direct handle
      return null;
    }
    try {
      @SuppressWarnings("unchecked")
      Function<P, T> function = (Function<P, T>) callSite.getTarget().invokeExact();
      return function;
    } catch (Throwable ex) {
      throw new AssertionError("Cannot convert to Function", ex);
    }
  }

  private static MethodHandle findConstructor(MethodHandles.Lookup lookup, Class<?> type) {
    return modifyReturnType(isPostProcessor(type), findConstructorWithExactReturnType(lookup, type));
  }

  private static MethodHandle findConstructorWithExactReturnType(MethodHandles.Lookup lookup, Class<?> type) {
    MethodHandle constructor;
    try {
      constructor = lookup.findConstructor(type, methodType(void.class));
    } catch (NoSuchMethodException | IllegalAccessException ex) {
      throw new AssertionError("Model class " + type.getName() + " has no empty public constructor!");
    }
    return constructor;
  }

  private static <T> T createInstance(Class<T> type, MethodHandle constructor) {
    try {
      return type.cast(constructor.invokeExact());
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable t) {
      throw new InstantiationException("Cannot construct " + type.getName(), t);
    }
  }

  private static final class EmptyConstructorInstantiator<T> extends Instantiator<T> implements Supplier<T> {
    EmptyConstructorInstantiator(Class<T> type, MethodHandle constructor) {
      super(type, modifyReturnType(isPostProcessor(type), constructor));
    }

    @Override
    public T get() {
      return createInstance(type, constructor);
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + " on " + type.getName();
  }

  @Override
  public boolean equals(Object obj) {
    return obj != null && obj.getClass().equals(getClass())
            && type.equals(((Instantiator<?>) obj).type);
  }

  @Override
  public int hashCode() {
    return type.hashCode() * 31 + 11;
  }

  private static final class ArgumentInstantiator<T, P> extends Instantiator<T> implements Function<P, T> {
    ArgumentInstantiator(Class<T> type, MethodHandle constructor) {
      super(type, constructor);
    }

    @Override
    public T apply(P p) {
      try {
        return type.cast(constructor.invokeExact(p));
      } catch (RuntimeException | Error e) {
        throw e;
      } catch (Throwable t) {
        throw new InstantiationException("Cannot construct " + type.getName(), t);
      }
    }
  }

  private static MethodHandle findConstructor(MethodHandles.Lookup lookup, Class<?> type, Class<?> expectedParameterType) {
    Constructor<?> empty = null;
    for (Constructor<?> c : type.getDeclaredConstructors()) {
      Class<?>[] parameterTypes = c.getParameterTypes();
      switch (parameterTypes.length) {
        case 0:
          empty = c;
          continue;
        case 1:
          Class<?> p = parameterTypes[0];
          if (Types.isAssignableFrom(expectedParameterType, p)) {
            return getConstructorWithArgument(lookup, type, expectedParameterType, c, p);
          }
      }
    }
    if (empty == null) {
      throw new AssertionError("No public constructor found for " + type.getName());
    }

    MethodHandle constructor;
    try {
      constructor = lookup.unreflectConstructor(empty);
    } catch (IllegalAccessException ex) {
      throw new AssertionError(ex);
    }
    constructor = MethodHandles.dropArguments(constructor, 0, Object.class);
    return Instantiator.modifyReturnType(isPostProcessor(type), constructor);
  }

  private static MethodHandle getConstructorWithArgument(MethodHandles.Lookup lookup, Class<?> type,
                                                         Class<?> expectedParameterType, Constructor<?> c,
                                                         Class<?> realParameterType) {

    MethodHandle constructor;
    try {
      constructor = lookup.unreflectConstructor(c);
    } catch (IllegalAccessException ex) {
      throw new AssertionError(ex);
    }
    boolean isMandatory = hasReadOnlyAnnotation(c.getParameterAnnotations()[0]);
    boolean isPostProcessor = isPostProcessor(type);
    if (!isMandatory && expectedParameterType.equals(realParameterType)) {
      return Instantiator.modifyReturnType(isPostProcessor, constructor, constructor.type().changeParameterType(0, Object.class));
    }

    MethodHandle argumentGuardHandle;
    if (realParameterType.isPrimitive()) {
      ArgumentGuard argumentGuard = new ArgumentGuard(type.getName(), Types.toWrapper(realParameterType));
      argumentGuardHandle = argumentGuard.createHandleForPrimitive(realParameterType);
    } else {
      ArgumentGuard argumentGuard = new ArgumentGuard(type.getName(), realParameterType);
      argumentGuardHandle = argumentGuard.createHandle(isMandatory);
    }
    constructor = MethodHandles.filterArguments(constructor, 0, argumentGuardHandle);
    return Instantiator.modifyReturnType(isPostProcessor, constructor);
  }

  private static boolean hasReadOnlyAnnotation(Annotation... annos) {
    for (Annotation a : annos) {
      if (a.annotationType().equals(Mandatory.class)) {
        return true;
      }
    }
    return false;
  }

  private static MethodHandle createHandleFor(Function<?, ?> argumentFilter, Class<?> expectedParameterType) {
    MethodHandle handle;
    try {
      handle = MethodHandles.publicLookup().bind(argumentFilter, "apply", methodType(Object.class, Object.class));
    } catch (NoSuchMethodException | IllegalAccessException ex) {
      throw new AssertionError("Can't access apply() method in Function", ex);
    }
    return handle.asType(handle.type().changeReturnType(expectedParameterType));
  }

  private static MethodHandle modifyReturnType(boolean isPostProcessor, MethodHandle constructor) {
    return modifyReturnType(isPostProcessor, constructor, constructor.type());
  }

  private static MethodHandle modifyReturnType(boolean isPostProcessor, MethodHandle constructor, MethodType methodType) {
    if (isPostProcessor) {
      constructor = constructor.asType(methodType.changeReturnType(PostProcessor.class));
      return MethodHandles.filterReturnValue(constructor, postProcessorHandle);
    }
    return constructor.asType(methodType.changeReturnType(Object.class));
  }

  private static boolean isPostProcessor(Class<?> type) {
    return PostProcessor.class.isAssignableFrom(type);
  }

  @SuppressWarnings("unused")
  private static Object postProcess(PostProcessor bean) {
    bean.postConstruct();
    return bean;
  }

  private static final MethodHandle postProcessorHandle;

  static {
    try {
      postProcessorHandle = MethodHandles.lookup().findStatic(Instantiator.class, "postProcess", methodType(Object.class, PostProcessor.class));
    } catch (IllegalAccessException | NoSuchMethodException ex) {
      throw new AssertionError(ex);
    }
  }

  private static class ArgumentGuard {
    private final String beanType;
    private final Class<?> expected;

    ArgumentGuard(String beanType, Class<?> expected) {
      this.beanType = beanType;
      this.expected = expected;
    }

    @SuppressWarnings("unused")
    private Object checkType(Object argument) {
      return argument == null ? null : checkMandatoryType(argument);
    }

    @SuppressWarnings("unused")
    private Object checkMandatoryType(Object argument) {
      if (!expected.isInstance(argument)) {
        throw new IllegalArgumentException(beanType + " expects " + expected.getName() + ", but argument was " + argument);
      }
      return argument;
    }

    private MethodHandle createHandle(Class<?> returnType, boolean mandatory) {
      try {
        MethodHandle checkHandle = MethodHandles.lookup().bind(this,
                mandatory ? "checkMandatoryType" : "checkType",
                methodType(Object.class, Object.class));
        return checkHandle.asType(checkHandle.type().changeReturnType(returnType));
      } catch (NoSuchMethodException | IllegalAccessException ex) {
        throw new AssertionError(ex);
      }
    }

    MethodHandle createHandle(boolean mandatory) {
      return createHandle(expected, mandatory);
    }

    MethodHandle createHandleForPrimitive(Class<?> primitiveType) {
      return createHandle(primitiveType, true);
    }
  }
}
