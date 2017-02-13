package org.fiolino.common.reflection;

import org.fiolino.common.util.Types;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MutableCallSite;

/**
 * Build a getter MethodHandle for some value that is nearly static final.
 *
 * It is meant for values that rather unlikely change, but have the option to do so. Unchanged values behave like
 * constants and are extremely fast, while changing the value may be very slow.
 *
 * The getter handle will usually be held in a static final variable. The instance of this class is used
 * to upate the value; it can be held anywhere.
 *
 * You may dismiss the instance of this class after handle creation if you don't need to change the value
 * any more.
 *
 * Created by Kuli on 10/11/2016.
 */
public class AlmostFinal<T> {
  private final Class<?> type;
  private final MutableCallSite callSite;

  private AlmostFinal(Class<?> type, Object initialValue) {
    this.type = type;
    callSite = new MutableCallSite(createGetterHandleFor(initialValue));
  }

  private MethodHandle createGetterHandleFor(Object value) {
    return MethodHandles.constant(type, value);
  }

  /**
   * Creates the getter handle for this value.
   *
   * @return A handle of type ()&lt;type&gt;
   */
  public MethodHandle createGetter() {
    return callSite.dynamicInvoker();
  }

  /**
   * Gets the current value. If the original value is of a primitive type, then return its wrapper object.
   *
   * While this method isn't really slow, it's not as fast as the getter method handle which is the preferred way
   * to get my value.
   */
  @SuppressWarnings("unchecked")
  public T get() {
    try {
      return (T) callSite.getTarget().invoke();
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  /**
   * Updates to a new value.
   *
   * Warning! This method may be slow.
   */
  public void updateTo(T newValue) {
    T oldValue = get();
    if (oldValue == newValue) {
      return;
    }
    callSite.setTarget(createGetterHandleFor(newValue));
    MutableCallSite.syncAll(new MutableCallSite[] {
             callSite
    });
  }

  /**
   * Creates a new instance for arbitrary Java objects.
   */
  public static <T> AlmostFinal<T> forReference(Class<T> type, T initialValue) {
    if (type.isPrimitive()) {
      throw new IllegalArgumentException(type.getName() + " not supported, please use a dedicated factory method!");
    }
    return new AlmostFinal<T>(type, initialValue);
  }

  /**
   * Creates a new instance for int constants.
   */
  public static AlmostFinal<Integer> forInt(int initialValue) {
    return new AlmostFinal<>(int.class, initialValue);
  }

  /**
   * Creates a new instance for long constants.
   */
  public static AlmostFinal<Long> forLong(long initialValue) {
    return new AlmostFinal<>(long.class, initialValue);
  }

  /**
   * Creates a new instance for short constants.
   */
  public static AlmostFinal<Short> forShort(short initialValue) {
    return new AlmostFinal<>(short.class, initialValue);
  }

  /**
   * Creates a new instance for byte constants.
   */
  public static AlmostFinal<Byte> forByte(byte initialValue) {
    return new AlmostFinal<>(byte.class, initialValue);
  }

  /**
   * Creates a new instance for float constants.
   */
  public static AlmostFinal<Float> forFloat(float initialValue) {
    return new AlmostFinal<>(float.class, initialValue);
  }

  /**
   * Creates a new instance for double constants.
   */
  public static AlmostFinal<Double> forDouble(double initialValue) {
    return new AlmostFinal<>(double.class, initialValue);
  }

  /**
   * Creates a new instance for char constants.
   */
  public static AlmostFinal<Character> forChar(char initialValue) {
    return new AlmostFinal<>(char.class, initialValue);
  }

  /**
   * Creates a new instance for boolean constants.
   */
  public static AlmostFinal<Boolean> forBoolean(boolean initialValue) {
    return new AlmostFinal<>(boolean.class, initialValue);
  }

  /**
   * Creates a new instance for any primitive type.
   */
  public static <T> AlmostFinal<T> forPrimitive(Class<T> wrapperType, T initialValue) {
    Class<?> primType = Types.asPrimitive(wrapperType);
    if (primType == null) {
      throw new IllegalArgumentException(wrapperType.getName() + " is not a primitive wrapper!");
    }
    return new AlmostFinal<>(primType, initialValue);
  }
}
