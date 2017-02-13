package org.fiolino.common.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.fiolino.common.reflection.Converters;
import org.fiolino.common.reflection.Methods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Deserializes data from strings that were created by a Serializer.
 *
 * Created by kuli on 23.03.15.
 */
public final class Deserializer {

  private static final Logger logger = LoggerFactory.getLogger(Deserializer.class);

  private static final MethodHandle EMPTY_STRING_CHECK;

  private static final MethodHandle STRING_TO_LIST_HANDLE;

  private static final MethodHandle LIST_ADD_HANDLE;

  private static final MethodHandle NEXT_STRING_HANDLE;

  private static final MethodHandle NEXT_EMBEDDED_HANDLE;

  private static final MethodHandle IGNORE_NEXT_HANDLE;

  static {
    MethodHandle stringToList;
    MethodHandle stringToStringArray;
    MethodHandles.Lookup lookup = lookup();
    try {
      EMPTY_STRING_CHECK = lookup.findVirtual(String.class, "isEmpty", methodType(boolean.class));
      stringToList = lookup.findStatic(Deserializer.class, "stringToList", methodType(List.class, MethodHandle.class, String[].class));
      stringToStringArray = lookup.findVirtual(String.class, "split", methodType(String[].class, String.class));

      NEXT_STRING_HANDLE = lookup.findVirtual(Pointer.class, "extractQuotedString", methodType(String.class));
      NEXT_EMBEDDED_HANDLE = lookup.findVirtual(Pointer.class, "extractInParenthesises", methodType(String.class));
      IGNORE_NEXT_HANDLE = lookup.findVirtual(Pointer.class, "ignoreNext", methodType(Pointer.class));
    } catch (NoSuchMethodException | IllegalAccessException ex) {
      throw new AssertionError(ex);
    }
    stringToStringArray = MethodHandles.insertArguments(stringToStringArray, 1, ",");
    STRING_TO_LIST_HANDLE = MethodHandles.filterArguments(stringToList, 1, stringToStringArray);

    @SuppressWarnings("unchecked")
    MethodHandle handle = Methods.findVia(publicLookup(), List.class, p -> p.add(new Object()));
    LIST_ADD_HANDLE = handle;
  }

  private MethodHandle factory; // (T,String)void; while building: (T,Reader)void

  private int ignoreNext;

  @SuppressWarnings("unused")
  private static List<?> stringToList(MethodHandle adder, String[] values) throws Throwable {
    List<Object> list = new ArrayList<>(values.length);
    for (String v : values) {
      adder.invokeExact(list, v);
    }
    return list;
  }

  public void addEmbedded(MethodHandle setter, Deserializer deserializer) {
    MethodHandle pointerSetter;
    Class<?> valueType = setter.type().parameterType(1);
    if (valueType.isAssignableFrom(List.class)) {
      throw new UnsupportedOperationException("Embedded lists not supported yet.");
    } else {
      MethodHandle constructor = findConstructor(valueType);
      MethodHandle deserializerHandle = deserializer.createDeserializerFor(constructor);
      pointerSetter = MethodHandles.filterArguments(setter, 1, deserializerHandle);
    }
    pointerSetter = Methods.secureNull(pointerSetter);
    pointerSetter = MethodHandles.filterArguments(pointerSetter, 1, NEXT_EMBEDDED_HANDLE);

    addHandle(pointerSetter);
  }

  private MethodHandle findConstructor(Class<?> valueType) {
    MethodHandle constructor;
    try {
      constructor = publicLookup().in(valueType).findConstructor(valueType, methodType(void.class));
    } catch (NoSuchMethodException | IllegalAccessException ex) {
      throw new AssertionError(valueType.getName() + " has no public constructor.", ex);
    }
    return constructor;
  }

  /**
   * Adds a method that sets some of my arguments.
   *
   * @param setter (&lt;? super T&gt;&lt;value type&gt;)void
   * @param genericType The value type
   */
  public void addSetter(MethodHandle setter, Type genericType) {
    MethodHandle pointerSetter;
    Class<?> valueType = setter.type().parameterType(1);
    if (String.class.equals(valueType)) {
      pointerSetter = Methods.secureNull(setter);
      pointerSetter = MethodHandles.filterArguments(pointerSetter, 1, NEXT_STRING_HANDLE);
      addHandle(pointerSetter);
      return;
    }

    if (valueType.isAssignableFrom(List.class)) {
      Class<?> itemType = Types.getRawArgument(genericType, Collection.class, 0, Types.Bounded.UPPER);
      MethodHandle addToList = LIST_ADD_HANDLE.asType(methodType(void.class, List.class, itemType));
      MethodHandle adder = createSetterForValueType(addToList, itemType);
      MethodHandle listBuilder = MethodHandles.insertArguments(STRING_TO_LIST_HANDLE, 0, adder);
      pointerSetter = MethodHandles.filterArguments(setter, 1, listBuilder);
    } else {
      pointerSetter = createSetterForValueType(setter, valueType);
    }
    pointerSetter = Methods.rejectIfArgument(pointerSetter, 1, EMPTY_STRING_CHECK);
    pointerSetter = Methods.secureNull(pointerSetter);
    pointerSetter = MethodHandles.filterArguments(pointerSetter, 1, NEXT_STRING_HANDLE);

    addHandle(pointerSetter);
  }

  private MethodHandle createSetterForValueType(MethodHandle setter, Class<?> valueType) {
    MethodHandle pointerSetter = Converters.convertArgumentTypesTo(setter, Converters.defaultConverters, 1, String.class);
    if (valueType.isEnum()) {
      pointerSetter = Methods.wrapWithExceptionHandler(pointerSetter, 1, IllegalArgumentException.class,
          (ex, vType, outputType, inputValue) ->
                  logger.warn("No such enum value " + inputValue + " in " + outputType.getName()));
    } else {
      pointerSetter = Methods.wrapWithExceptionHandler(pointerSetter, 1, NumberFormatException.class,
              (ex, vType, outputType, inputValue) ->
          logger.warn("Illegal number value " + inputValue));
    }
    return pointerSetter;
  }

  /**
   * Adds a setter for the next field.
   *
   * @param setter (&lt;? super T&gt;,String)void
   */
  private void addHandle(MethodHandle setter) {
    MethodHandle handleIgnoringBefore = setter.asType(setter.type().changeReturnType(void.class));
    while (ignoreNext > 0) {
      handleIgnoringBefore = MethodHandles.filterArguments(handleIgnoringBefore, 1, IGNORE_NEXT_HANDLE);
      ignoreNext--;
    }
    if (factory == null) {
      factory = handleIgnoringBefore;
    } else {
      factory = factory.asType(factory.type().changeParameterType(0, setter.type().parameterType(0)));
      factory = MethodHandles.foldArguments(handleIgnoringBefore, factory);
    }

  }

  public void ignoreNext() {
    ignoreNext++;
  }

  /**
   * Creates a MethodHandle that accepts a String and returns a deserialized  Object.
   *
   * @return (String)&lt;type&gt;
   */
  public MethodHandle createDeserializerFor(Class<?> valueType) {
    return createDeserializerFor(findConstructor(valueType));
  }

  /**
   * Creates a MethodHandle that accepts a String and returns a deserialized  Object.
   *
   * @return (String)&lt;type&gt;
   */
  public MethodHandle createDeserializerFor(MethodHandle constructor) {
    if (factory == null) {
      throw new IllegalStateException("No setters defined yet.");
    }
    // factory is (? super T,String)void
    MethodHandle pointerConstructor;
    try {
      pointerConstructor = lookup().findConstructor(Pointer.class, methodType(void.class, String.class));
    } catch (NoSuchMethodException | IllegalAccessException ex) {
      throw new AssertionError("No (String) constructor in class Pointer?", ex);
    }
    MethodHandle handle = MethodHandles.filterArguments(factory, 1, pointerConstructor);
    Class<?> modelType = constructor.type().returnType();
    // handle is (? super T,String)void
    handle = handle.asType(methodType(void.class, modelType, String.class));
    // handle is (T,String)void
    handle = Methods.returnArgument(handle, 0);
    // handle is (Object,String)Object
    handle = MethodHandles.foldArguments(handle, constructor);
    // handle is (String)<type>
    handle = Methods.rejectIfArgument(handle, 0, EMPTY_STRING_CHECK);
    return Methods.secureNull(handle);
  }

  @SuppressWarnings("unused")
  private static final class Pointer {
    final String input;
    int position;
    char lastStop = Character.UNASSIGNED;

    Pointer(String input) {
      this.input = input;
    }

    String next() {
      return extractUntil(':');
    }

    Pointer ignoreNext() {
      next();
      return this;
    }

    String extractUntil(char... characters) {
      if (position < 0) {
        return null;
      }
      int nextPos = Integer.MAX_VALUE;
      for (char ch : characters) {
        int n = input.indexOf(ch, position);
        if (n >= 0 && n < nextPos) {
          nextPos = n;
          lastStop = ch;
        }
      }
      if (nextPos == Integer.MAX_VALUE) {
        int lastPosition = position;
        position = -1;
        lastStop = Character.UNASSIGNED;
        return input.substring(lastPosition);
      }
      String sub = input.substring(position, nextPos);
      position = nextPos + 1;
      return sub;
    }

    String extractQuotedString() {
      if (position < 0 || position >= input.length()) {
        return null;
      }
      if (input.charAt(position) != '"') {
        String s = next();
        return s == null || s.length() > 0 ? s : null;
      }
      position++;
      StringBuilder sb = new StringBuilder();
      if (extractUntilEndQuote(sb, false)) {
        next(); // Forward to colon - value should be empty
      }
      return sb.toString();
    }

    private boolean extractUntilEndQuote(StringBuilder sb, boolean keepEscapeSequences) {
      while (true) {
        String snippet = extractUntil('"', '\\');
        sb.append(snippet);
        if (lastStop == '\\') {
          // Escape sequence
          if (position >= input.length()) {
            sb.append('\\');
            return false;
          }
          if (keepEscapeSequences) {
            sb.append('\\');
          }
          sb.append(input.charAt(position++));
        } else {
          // Was closing quote
          return true;
        }
      }
    }

    String extractInParenthesises() {
      if (position < 0 || position >= input.length()) {
        return null;
      }
      if (input.charAt(position) != '(') {
        String s = next();
        return s == null || s.length() > 0 ? s : null;
      }
      position++;
      StringBuilder sb = new StringBuilder();
      while (true) {
        String snippet = extractUntil(')', '"');
        sb.append(snippet);
        if (lastStop == '"') {
          // Parenthesis within quotes
          if (position >= input.length()) {
            return sb.append('"').toString();
          }
          sb.append('"'); // Append opening quote
          if (extractUntilEndQuote(sb, true)) {
            sb.append('"'); // Append closing quote
          }
        } else {
          position++;
          return sb.toString();
        }
      }
    }
  }
}
