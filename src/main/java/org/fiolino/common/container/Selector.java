package org.fiolino.common.container;

import java.util.function.Supplier;

/**
 * The selector is the key in a {@link Container}. It gets registered by a corresponding {@link Schema}.
 *
 * @author Michael Kuhlmann <michael@kuhlmann.org>
 */
public class Selector<T> {
  private final int position;
  private final Schema schema;
  private final Schema.Protected protection;
  private boolean isInherited = true;

  /**
   * Constructor called from the Schema instance.
   *
   * @param schema The owner
   * @param position Position in the container's value array
   * @param protection Used to restrict write access
   */
  Selector(Schema schema, int position, Schema.Protected protection) {
    this.schema = schema;
    this.position = position;
    this.protection = protection;
  }

  /**
   * Changes the inherited flag.
   *
   * When a selector is inherited, then each sub container will query its parent when there's no
   * local value available. If this is set to false, then only local values will be returned
   * by calling get().
   */
  public Selector<T> setInherited(boolean isInherited) {
    this.isInherited = isInherited;
    return this;
  }

  final int getPosition() {
    return position;
  }

  void validateOwner(Schema toCheck) {
    if (toCheck != schema) {
      throw new IllegalStateException("Cannot work with " + this + " on " + toCheck);
    }
  }

  /**
   * Gets a value from a given {@link Container}.
   */
  public T get(Container container) {
    return container.get(this);
  }

  Object getDirectlyFromParent(Container parent) {
    return isInherited ? parent.getDirectly(this) : null;
  }

  /**
   * Assigns a value.
   */
  public void set(Container container, T value) {
    container.set(this, value);
  }

  void checkWriteAccess(Supplier<Object> previousValue) {
    switch (protection) {
      case WRITE_ONCE:
        Object v = previousValue.get();
        if (v != null) {
          throw new IllegalAccessError("Protected access: " + this + " has already the assigned value " + v);
        }
        break;
      case PUBLIC:
        // Nothing to check
        break;
      default:
        throw new AssertionError("Unknown protection " + protection);
    }
  }

  @SuppressWarnings("unchecked")
  T cast(Object value) {
    return (T) value;
  }

  T initialValue(ReadOnlyContainer container) {
    return null;
  }

  T castOrDefault(Object value, ReadOnlyContainer container) {
    return value == null ? initialValue(container) : cast(value);
  }

  @Override
  public String toString() {
    return "Selector " + protection + " #" + position + " of " + schema;
  }

  static final class SelectorWithDefaultValue<T> extends Selector<T> {

    private final T defaultValue;

    SelectorWithDefaultValue(Schema schema, int position, T defaultValue, Schema.Protected protection) {
      super(schema, position, protection);
      this.defaultValue = defaultValue;
    }

    @Override
    public T initialValue(ReadOnlyContainer container) {
      return defaultValue;
    }
  }

  static final class SelectorWithLazyDefaultValue<T> extends Selector<T> {

    private enum Null {VALUE}

    private final Supplier<T> factory;
    private Object resolvedValue;

    SelectorWithLazyDefaultValue(Schema schema, int position, Supplier<T> factory, Schema.Protected protection) {
      super(schema, position, protection);
      this.factory = factory;
    }

    @Override
    public T initialValue(ReadOnlyContainer container) {
      Object resolved = resolvedValue;
      if (resolved == null) {
        T newDefault = factory.get();
        resolved = newDefault == null ? Null.VALUE : newDefault;
        resolvedValue = resolved;
        return newDefault;
      }
      return resolved == Null.VALUE ? null : cast(resolved);
    }
  }

  static final class SelectorWithDefaultAlias<T> extends Selector<T> {

    private final Selector<? extends T> alias;

    SelectorWithDefaultAlias(Schema schema, int position, Selector<? extends T> alias, Schema.Protected protection) {
      super(schema, position, protection);
      this.alias = alias;
    }

    @Override
    public T initialValue(ReadOnlyContainer container) {
      return container.get(alias);
    }
  }
}
