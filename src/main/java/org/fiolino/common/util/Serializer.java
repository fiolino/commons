package org.fiolino.common.util;

import org.fiolino.common.analyzing.ClassWalker;
import org.fiolino.common.reflection.Methods;
import org.fiolino.data.annotation.SerialFieldIndex;
import org.fiolino.data.annotation.SerializeEmbedded;
import org.fiolino.data.base.Identified;
import org.fiolino.data.base.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static java.lang.invoke.MethodType.methodType;

/**
 * Serializes instances into Strings.
 *
 * The serialized class should have annotations @{@link SerialFieldIndex} or @{@link SerializeEmbedded}
 * on its fields or getters/setters.
 *
 * Individual values are separated by a colon.
 *
 * Created by kuli on 29.12.15.
 */
public class Serializer<T> implements SerialPresenter {

  private static final Logger logger = LoggerFactory.getLogger(Serializer.class);
  private static final ClassValue<Serializer<?>> serializers = new ClassValue<Serializer<?>>() {
    @Override
    protected Serializer<?> computeValue(Class<?> type) {
      Serializer<?> serializer = new Serializer<>(type);
      MethodHandles.Lookup lookup = MethodHandles.publicLookup().in(type);
      serializer.analyze(lookup);
      return serializer;
    }
  };

  @SuppressWarnings("unchecked")
  public static <T> Serializer<T> get(Class<T> type) {
    return (Serializer<T>) serializers.get(type);
  }

  private void analyze(final MethodHandles.Lookup lookup) {
    ClassWalker<RuntimeException> walker = new ClassWalker<>();
    walker.onField(f -> {
        SerialFieldIndex fieldAnno = f.getAnnotation(SerialFieldIndex.class);
        SerializeEmbedded embedAnno = f.getAnnotation(SerializeEmbedded.class);
        if (fieldAnno == null && embedAnno == null) {
          return;
        }
        MethodHandle getter = Methods.findGetter(lookup, f);
        if (getter == null) {
          logger.warn("No getter for " + f);
          return;
        }
        String name = f.getName();
        if (fieldAnno != null) {
          setSerialField(name, getter, f.getGenericType(), fieldAnno.value());
        }
        if (embedAnno != null) {
          setEmbedded(name, getter, f.getGenericType(), embedAnno.value());
        }
    });

    walker.onMethod(m -> {
        SerialFieldIndex fieldAnno = m.getAnnotation(SerialFieldIndex.class);
        SerializeEmbedded embedAnno = m.getAnnotation(SerializeEmbedded.class);
        if (fieldAnno == null && embedAnno == null) {
          return;
        }
        if (m.getParameterCount() != 0 || m.getReturnType() == void.class) {
          if (logger.isDebugEnabled()) {
            logger.debug("Ignoring " + m + " because it's not a getter.");
          }
          return;
        }
        MethodHandle getter;
        try {
          getter = lookup.unreflect(m);
        } catch (IllegalAccessException e) {
          logger.warn(m + " is not accessible!");
          return;
        }
        String name = m.getName();
        if (fieldAnno != null) {
          setSerialField(name, getter, m.getGenericReturnType(), fieldAnno.value());
        }
        if (embedAnno != null) {
          setEmbedded(name, getter, m.getGenericReturnType(), embedAnno.value());
        }
    });

    walker.analyze(lookup.lookupClass());
    validateNotEmpty();
  }

  private static class EmptyPresenter implements SerialPresenter {
    static final EmptyPresenter INSTANCE = new EmptyPresenter();

    @Override
    public void printInto(StringBuilder sb, Object value) {
      // Nothing to do
    }

    @Override
    public void printDescription(StringBuilder sb) {
      // Print nothing
    }
  }

  private static abstract class GetterBasedPresenter implements SerialPresenter {
    final MethodHandle getter;
    private final String name;

    GetterBasedPresenter(String name, MethodHandle getter) {
      this.name = name;
      this.getter = getter.asType(getter.type().changeParameterType(0, Object.class));
    }

    GetterBasedPresenter(String name, MethodHandle getter, Class<?> returnType) {
      this.name = name;
      this.getter = getter.asType(methodType(returnType, Object.class));
    }

    @Override
    public void printDescription(StringBuilder sb) {
      sb.append(name);
    }
  }

  private static class LongBasedPresenter extends GetterBasedPresenter {
    LongBasedPresenter(String name, MethodHandle getter) {
      super(name, getter);
    }

    @Override
    public void printInto(StringBuilder sb, Object value) throws Exception {
      long val;
      try {
        val = (long) getter.invokeExact(value);
      } catch (Error | Exception e) {
        throw e;
      } catch (Throwable t) {
        throw new AssertionError(t);
      }
      sb.append(val);
    }
  }

  private static class IntBasedPresenter extends GetterBasedPresenter {
    IntBasedPresenter(String name, MethodHandle getter) {
      super(name, getter);
    }

    @Override
    public void printInto(StringBuilder sb, Object value) throws Exception {
      int val;
      try {
        val = (int) getter.invokeExact(value);
      } catch (Error | Exception e) {
        throw e;
      } catch (Throwable t) {
        throw new AssertionError(t);
      }
      sb.append(val);
    }
  }

  private static class BooleanBasedPresenter extends GetterBasedPresenter {
    BooleanBasedPresenter(String name, MethodHandle getter) {
      super(name, getter);
    }

    @Override
    public void printInto(StringBuilder sb, Object value) throws Exception {
      boolean val;
      try {
        val = (boolean) getter.invokeExact(value);
      } catch (Error | Exception e) {
        throw e;
      } catch (Throwable t) {
        throw new AssertionError(t);
      }
      sb.append(val ? 't' : 'f');
    }
  }

  private static class StringBasedPresenter extends GetterBasedPresenter {
    StringBasedPresenter(String name, MethodHandle getter) {
      super(name, getter);
    }

    @Override
    public void printInto(StringBuilder sb, Object value) throws Exception {
      String val;
      try {
        val = (String) getter.invokeExact(value);
      } catch (Error | Exception e) {
        throw e;
      } catch (Throwable t) {
        throw new AssertionError(t);
      }
      if (val == null) {
        return;
      }
      if (shouldBeQuoted(val)) {
        Strings.appendQuotedString(sb, val);
      } else {
        sb.append(val);
      }
    }
  }

  private static final CharSet QUOTED_CHARACTERS = CharSet.of(":,()");

  private static boolean shouldBeQuoted(String val) {
    return val.isEmpty() || QUOTED_CHARACTERS.isContainedIn(val) || val.charAt(0) == '"';
  }

  private static class DateBasedPresenter extends GetterBasedPresenter {
    DateBasedPresenter(String name, MethodHandle getter) {
      super(name, getter, Date.class);
    }

    @Override
    public void printInto(StringBuilder sb, Object value) throws Exception {
      Date val;
      try {
        val = (Date) getter.invokeExact(value);
      } catch (Error | Exception e) {
        throw e;
      } catch (Throwable t) {
        throw new AssertionError(t);
      }
      if (val == null) {
        return;
      }
      sb.append(val.getTime());
    }
  }

  private static class TextBasedPresenter extends GetterBasedPresenter {
    TextBasedPresenter(String name, MethodHandle getter) {
      super(name, getter);
    }

    @Override
    public void printInto(StringBuilder sb, Object value) throws Exception {
      Text text;
      try {
        text = (Text) getter.invokeExact(value);
      } catch (Error | Exception e) {
        throw e;
      } catch (Throwable t) {
        throw new AssertionError(t);
      }
      if (text == null) {
        return;
      }
      String val = text.getText();
      if (shouldBeQuoted(val)) {
        Strings.appendQuotedString(sb, val);
      } else {
        sb.append(val);
      }
    }
  }

  private static class IdentifiedBasedPresenter extends GetterBasedPresenter {
    IdentifiedBasedPresenter(String name, MethodHandle getter) {
      super(name, getter, Identified.class);
    }

    @Override
    public void printInto(StringBuilder sb, Object value) throws Exception {
      Identified identified;
      try {
        identified = (Identified) getter.invokeExact(value);
      } catch (Error | Exception e) {
        throw e;
      } catch (Throwable t) {
        throw new AssertionError(t);
      }
      if (identified != null) {
        sb.append(identified.getId());
      }
    }
  }

  private static class ListBasedPresenter extends GetterBasedPresenter {

    ListBasedPresenter(String name, MethodHandle getter) {
      super(name, getter, Iterable.class);
    }

    @Override
    public void printInto(StringBuilder sb, Object value) throws Exception {
      Iterable<?> list;
      try {
        list = (Iterable<?>) getter.invokeExact(value);
      } catch (Error | Exception e) {
        throw e;
      } catch (Throwable t) {
        throw new AssertionError(t);
      }
      if (list != null) {
        boolean first = true;
        for (Object v : list) {
          if (first) {
            first = false;
          } else {
            sb.append(',');
          }
          if (v == null) {
            return;
          }
          if (v instanceof Text) {
            v = ((Text) v).getText();
          }
          if (v instanceof String) {
            String val = (String) v;
            if (shouldBeQuoted(val)) {
              Strings.appendQuotedString(sb, val);
            } else {
              sb.append(val);
            }
            return;
          }
          if (v instanceof Identified) {
            sb.append(((Identified) v).getId());
            return;
          }
          sb.append(v);
        }
      }
    }
  }

  private static class MiscBasedPresenter extends GetterBasedPresenter {
    MiscBasedPresenter(String name, MethodHandle getter) {
      super(name, getter, Object.class);
    }

    @Override
    public void printInto(StringBuilder sb, Object value) throws Exception {
      Object val;
      try {
        val = getter.invokeExact(value);
      } catch (Error | Exception e) {
        throw e;
      } catch (Throwable t) {
        throw new AssertionError(t);
      }
      if (val != null) {
        sb.append(val);
      }
    }
  }

  private SerialPresenter[] parts = new SerialPresenter[0];
  private final Class<T> type;

  private Serializer(Class<T> type) {
    this.type = type;
  }

  private void validateNotEmpty() {
    if (parts.length == 0) {
      throw new IllegalStateException("No serialized fields in " + type.getName());
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("Serializer for ").append(type.getName()).append(' ');
    printDescription(sb);
    return sb.toString();
  }

  @Override
  public void printDescription(StringBuilder sb) {
    sb.append('(');
    boolean first = true;
    for (SerialPresenter p : parts) {
      if (first) {
        first = false;
      } else {
        sb.append(':');
      }
      p.printDescription(sb);
    }
    sb.append(')');
  }

  private void addPresenter(int pos, SerialPresenter builder) {
    int n = parts.length;
    if (pos >= n) {
      parts = Arrays.copyOf(parts, pos + 1);
      Arrays.fill(parts, n, pos, EmptyPresenter.INSTANCE);
      parts[pos] = builder;
      return;
    }
    parts[pos] = builder;
  }

  private void setSerialField(String name, MethodHandle getter, Type type, int fieldIndex) {
    SerialPresenter builder = getSerialPresenterFor(name, type, getter);
    addPresenter(fieldIndex, builder);
  }

  private SerialPresenter getSerialPresenterFor(String name, Type valueType, MethodHandle getter) {
    if (valueType == long.class) {
      return new LongBasedPresenter(name, getter);
    }
    if (valueType == int.class) {
      return new IntBasedPresenter(name, getter);
    }
    if (valueType == boolean.class) {
      return new BooleanBasedPresenter(name, getter);
    }
    if (valueType == String.class) {
      return new StringBasedPresenter(name, getter);
    }
    if (valueType == Text.class) {
      return new TextBasedPresenter(name, getter);
    }
    Class<?> c = Types.getRawType(valueType);
    if (Date.class.isAssignableFrom(c)) {
      return new DateBasedPresenter(name, getter);
    }
    if (Identified.class.isAssignableFrom(c)) {
      return new IdentifiedBasedPresenter(name, getter);
    }
    if (Iterable.class.isAssignableFrom(c)) {
      return new ListBasedPresenter(name, getter);
    }

    return new MiscBasedPresenter(name, getter);
  }

  @Override
  public void printInto(StringBuilder sb, Object value) throws Exception {
    boolean first = true;
    for (SerialPresenter p : parts) {
      if (first) {
        first = false;
      } else {
        sb.append(':');
      }
      p.printInto(sb, value);
    }
  }

  public final String serialize(T value) {
    StringBuilder sb = new StringBuilder();
    try {
      printInto(sb, value);
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable t) {
      throw new AssertionError("Cannot sereialize " + value, t);
    }
    return sb.toString();
  }

  private void setEmbedded(String name, MethodHandle getter, Type type, int fieldIndex) {
    SerialPresenter subPresenter;
    Class<?> rawType = Types.getRawType(type);
    if (Iterable.class.isAssignableFrom(rawType)) {
      Class<?> targetType = Types.getRawArgument(type, List.class, 0, Types.Bounded.UPPER);
      Serializer<?> targetSerializer = Serializer.get(targetType);
      subPresenter = new EmbeddedMultiSerializer(name, getter, targetSerializer);
    } else {
      Serializer<?> targetSerializer = Serializer.get(rawType);
      subPresenter = new EmbeddedSerializer(name, getter, targetSerializer);
    }
    addPresenter(fieldIndex, subPresenter);
  }

  private static class EmbeddedSerializer extends GetterBasedPresenter {
    private final Serializer<?> serializer;

    EmbeddedSerializer(String name, MethodHandle getter, Serializer<?> serializer) {
      super(name, getter, Object.class);
      this.serializer = serializer;
    }

    @Override
    public void printInto(StringBuilder sb, Object value) throws Exception {
      Object relation;
      try {
        relation = getter.invokeExact(value);
      } catch (Error | Exception e) {
        throw e;
      } catch (Throwable t) {
        throw new AssertionError(t);
      }
      if (relation != null) {
        sb.append('(');
        serializer.printInto(sb, relation);
        sb.append(')');
      }
    }

    @Override
    public void printDescription(StringBuilder sb) {
      super.printDescription(sb);
      serializer.printDescription(sb);
    }
  }

  private static class EmbeddedMultiSerializer extends GetterBasedPresenter {
    private final Serializer<?> serializer;

    EmbeddedMultiSerializer(String name, MethodHandle getter, Serializer<?> serializer) {
      super(name, getter, Iterable.class);
      this.serializer = serializer;
    }

    @Override
    public void printInto(StringBuilder sb, Object value) throws Exception {
      Iterable<?> relationValues;
      try {
        relationValues = (Iterable<?>) getter.invokeExact(value);
      } catch (Error | Exception e) {
        throw e;
      } catch (Throwable t) {
        throw new AssertionError(t);
      }
      if (relationValues != null) {
        boolean first = true;
        for (Object v : relationValues) {
          if (first) {
            first = false;
          } else {
            sb.append(',');
          }
          sb.append('(');
          serializer.printInto(sb, v);
          sb.append(')');
        }
      }
    }

    @Override
    public void printDescription(StringBuilder sb) {
      super.printDescription(sb);
      serializer.printDescription(sb);
    }
  }
}
