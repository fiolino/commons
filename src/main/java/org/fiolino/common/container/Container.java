package org.fiolino.common.container;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Contains various values based on some Schema.
 */
public class Container implements ReadOnlyContainer {

    private static final Container EMPTY = new Schema("<EMPTY>").createContainer();

    private final Schema owner;
    private Object[] values;

    Container(Schema owner, int size) {
        this.owner = owner;
        values = new Object[size];
    }

    public static Container empty() {
        return EMPTY;
    }

    private final class Startup implements ReadOnlyContainer {

        @Override
        public <E> E get(Selector<E> selector) {
            TemporaryFunction cycleDetector = new TemporaryFunction();
            return cycleDetector.get(selector);
        }
    }

    private static final ReadOnlyContainer ALL_NULL = new ReadOnlyContainer() {
        @Override
        public <E> E get(Selector<E> selector) {
            return null;
        }
    };

    private final ReadOnlyContainer startup = new Startup();

    private final class TemporaryFunction implements ReadOnlyContainer {
        private final Set<Selector<?>> used = new HashSet<>();

        @Override
        public <E> E get(Selector<E> selector) {
            if (!used.add(selector)) {
                throw new IllegalStateException("Cyclic aliases detected!");
            }
            return Container.this.get(selector, this);
        }
    }

    @Override
    public <E> E get(Selector<E> selector) {
        return get(selector, startup);
    }

    public <E> E getDirectOnly(Selector<E> selector) {
        return get(selector, ALL_NULL);
    }

    Object getDirectly(Selector<?> selector) {
        int pos = owner.getIndex(selector);
        if (!withinRange(pos))
            return null;

        return values[pos];
    }

    private <E> E get(Selector<E> selector, ReadOnlyContainer caller) {
        Object val = getDirectly(selector);
        return selector.castOrDefault(val, caller);
    }

    public <E> E getOrCreate(Selector<E> selector, Supplier<? extends E> factory) {
        int pos = owner.getIndex(selector);
        Object val;
        if (!checkLength(pos) || (val = values[pos]) == null) {
            E newVal = factory.get();
            if (newVal == null) {
                throw new NullPointerException("Factory " + factory + " returned null!");
            }

            selector.checkWriteAccess(null);
            values[pos] = newVal;
            return newVal;
        }
        return selector.cast(val);
    }

    public <E> void set(Selector<E> selector, E value) {
        if (value == null) {
            return;
        }
        int pos = owner.getIndex(selector);
        checkLength(pos);
        selector.checkWriteAccess(() -> values[pos]);
        values[pos] = value;
    }

    /**
     * Sets the value if it was not set before.
     *
     * @return The existing valuem, which will be null if not set before
     */
    public <E> E putIfAbsent(Selector<E> selector, E value) {
        int pos = owner.getIndex(selector);
        Object previous = checkLength(pos) ? values[pos] : null;
        if (previous == null) {
            selector.checkWriteAccess(() -> null);
            values[pos] = value;
            return null;
        }
        return selector.cast(previous);
    }

    /**
     * Gets the value, or the default value,
     * Does not take aliases and such into account.
     */
    public <E> E getOrDefault(Selector<E> selector, E value) {
        E result = getDirectOnly(selector);
        return result == null ? value : result;
    }

    private boolean withinRange(int pos) {
        return pos < values.length;
    }

    private boolean checkLength(int pos) {
        if (!withinRange(pos)) {
            values = Arrays.copyOf(values, pos + 1);
            return false;
        }
        return true;
    }

    public <E> E getAndSet(Selector<E> selector, E value) {
        int pos = owner.getIndex(selector);
        checkLength(pos);
        E previous = selector.castOrDefault(values[pos], startup);
        //selector.checkWriteAccess(previous);
        values[pos] = value;
        return previous;
    }

    public <E> E remove(Selector<E> selector) {
        int pos = owner.getIndex(selector);
        if (pos < values.length) {
            selector.checkWriteAccess(() -> values[pos]);
            E previous = selector.cast(values[pos]);
            values[pos] = null;
            return previous;
        }
        return null;
    }

    /**
     * If there is a value assigned, then execute the Consumer and return true.
     * Otherwise simply return false.
     */
    public <E> boolean ifValid(Selector<E> selector, Consumer<? super E> action) {
        E val = get(selector);
        if (val != null) {
            action.accept(val);
            return true;
        }
        return false;
    }

    /**
     * If there is a value assigned, then return the result of the Predicate.
     * Otherwise simply return false.
     */
    public <E> boolean ifValidAnd(Selector<E> selector, Predicate<? super E> action) {
        E val = get(selector);
        return val != null && action.test(val);
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this ||
                obj != null && obj.getClass().equals(getClass()) &&
                        ((Container) obj).owner.equals(owner) && Arrays.equals(((Container) obj).values, values);
    }

    @Override
    public int hashCode() {
        return owner.hashCode() * 31 + Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return "Container of " + owner;
    }

    public Container createSubContainer() {
        return new SubContainer(this);
    }

    private static final class SubContainer extends Container {
        private final Container parent;

        SubContainer(Container parent) {
            super(parent.owner, parent.values.length);
            this.parent = parent;
        }

        @Override
        Object getDirectly(Selector<?> selector) {
            Object val = super.getDirectly(selector);
            return val == null ? selector.getDirectlyFromParent(parent) : val;
        }

        @Override
        public <E> E getDirectOnly(Selector<E> selector) {
            Object value = super.getDirectly(selector);
            return value == null ? null : selector.cast(value);
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj) && parent.equals(((SubContainer) obj).parent);
        }

        @Override
        public int hashCode() {
            return parent.hashCode() * 31 + super.hashCode();
        }

        @Override
        public String toString() {
            return super.toString() + " --- in parent " + parent.toString();
        }
    }
}
