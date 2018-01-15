package org.fiolino.common.container;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Defines the data structure used in Containers.
 */
public class Schema {

    public enum Protected {
        /**
         * Every class can read or write.
         */
        PUBLIC,
        /**
         * Cannot be written as soon as value != null.
         */
        WRITE_ONCE;
    }

    Selector<?>[] selectors = new Selector<?>[0];
    private final String name;

    public Schema(String name) {
        this.name = name;
    }

    void add(Selector<?> selector) {
        int n = selectors.length;
        Selector<?>[] newArray = Arrays.copyOf(selectors, n + 1);
        newArray[n] = selector;
        selectors = newArray;
    }

    public <T> Selector<T> createSelector() {
        int n = selectors.length;
        Selector<T> sel = new Selector<>(this, n, Protected.PUBLIC);
        add(sel);

        return sel;
    }

    public <T> Selector<T> createSelector(T defaultValue) {
        int n = selectors.length;
        Selector<T> sel = new Selector.SelectorWithDefaultValue<>(this, n, defaultValue, Protected.PUBLIC);
        add(sel);

        return sel;
    }

    public <T> Selector<T> createSelectorWithAlias(Selector<? extends T> alias) {
        int n = selectors.length;
        Selector<T> sel = new Selector.SelectorWithDefaultAlias<>(this, n, alias, Protected.PUBLIC);
        add(sel);

        return sel;
    }

    /**
     * Creates a selector where the given supplier creates a new instance and assigns it as the value
     * to the container when no individual value was assigned so far.
     * <p>
     * The given supplier is called at most once per Container instance as long as the value is not removed
     * manually.
     */
    public <T> Selector<T> createLazilyInitializedSelector(Supplier<T> supplier) {
        int n = selectors.length;
        Selector<T> sel = new Selector.SelectorWithLazyDefaultValue<>(this, n, supplier, Protected.PUBLIC);
        add(sel);

        return sel;
    }

    public Container createContainer() {
        return new Container(this, selectors.length);
    }

    public Optional<Selector<?>> find(Predicate<Selector<?>> tester) {
        return Arrays.stream(selectors).filter(tester).findAny();
    }

    @Override
    public String toString() {
        return "Schema '" + name + "'";
    }

    int getIndex(Selector<?> sel) {
        return sel.getPosition();
    }
}
