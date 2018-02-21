package org.fiolino.common.util;

import javax.annotation.Nullable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * This class can be used to hold values that usually don't change,
 * but should be frequently updated to accept changes if there are some.
 * <p>
 * This is best used for tasks that need some resources, but not that many
 * that frequent updates would hurt. An example is some data read from the
 * file system.
 * <p>
 * Values are calculated by either a {@link Supplier} or a {@link UnaryOperator}, which gets the old
 * cached value as the parameter. This can be useful for costly operations which can check whether
 * an update is necessary at all.
 * <p>
 * Operators are only called once even in concurrent access. They don't need to be thread safe even in concurrent
 * environments.
 * <p>
 * Example:
 * <code>
 * Cached&lt;DataObject&gt; valueHolder = Cached.updateEvery(5).hours().with(() -&gt; new DataObject(...));
 * </code>
 *
 * @author kuli
 */
public abstract class Cached<T> implements Supplier<T> {

    /**
     * This is the starting factory method, followed by a method for the time unit.
     *
     * @param value How many time units shall the cache keep its value.
     */
    public static ExpectUnit updateEvery(long value) {
        return new ExpectUnit(value);
    }

    /**
     * This is the factory method which assigns a duration in text form, including the delay and the time unit.
     *
     * Examples:
     * 1 Day
     * 5 sec
     * 900 millis
     *
     * The time unit must be a unique start of some {@link TimeUnit} name. If none is given, seconds are assumed.
     */
    public static ExpectEvaluator updateEvery(String value) {
        long duration = Strings.parseLongDuration(value);
        return new ExpectEvaluator(TimeUnit.NANOSECONDS.toMillis(duration));
    }

    /**
     * Use this factory to create a cached instance which calls its operator in every call.
     */
    public static <T> Cached<T> updateAlways(T initialValue, UnaryOperator<T> evaluator) {
        return new ImmediateUpdater<>(initialValue, evaluator, true);
    }

    /**
     * Use this factory to create a cached instance which calls its operator in every call.
     * Calculated value may be null.
     */
    public static <T> Cached<T> updateAlwaysNullable(T initialValue, UnaryOperator<T> evaluator) {
        return new ImmediateUpdater<>(initialValue, evaluator, false);
    }

    /**
     * Creates a Cached instance that gets initialized by some supplier and then always returns
     * that value.
     *
     * @param evaluator Computes the initial value
     * @param <T> The type
     * @return A Cached instance
     */
    public static <T> Cached<T> with(Supplier<T> evaluator) {
        return new OneTimeInitializer<>(evaluator, true);
    }

    /**
     * Creates a Cached instance that gets initialized by some supplier and then always returns
     * that value.
     * Calculated value may be null.
     *
     * @param evaluator Computes the initial value
     * @param <T> The type
     * @return A Cached instance
     */
    public static <T> Cached<T> withNullable(Supplier<T> evaluator) {
        return new OneTimeInitializer<>(evaluator, false);
    }

    public static final class ExpectUnit {
        private final long value;

        private ExpectUnit(long value) {
            this.value = value;
        }

        /**
         * Sets the expiration timeout unit to nano seconds.
         * <p>
         * This method follows a with().
         */
        public ExpectEvaluator nanoseconds() {
            return new ExpectEvaluator(TimeUnit.NANOSECONDS.toMillis(value));
        }

        /**
         * Sets the expiration timeout unit to micro seconds.
         * <p>
         * This method follows a with().
         */
        public ExpectEvaluator microseconds() {
            return new ExpectEvaluator(TimeUnit.MICROSECONDS.toMillis(value));
        }

        /**
         * Sets the expiration timeout unit to milli seconds.
         * <p>
         * This method follows a with().
         */
        public ExpectEvaluator milliseconds() {
            return new ExpectEvaluator(value);
        }

        /**
         * Sets the expiration timeout unit to seconds.
         * <p>
         * This method follows a with().
         */
        public ExpectEvaluator seconds() {
            return new ExpectEvaluator(TimeUnit.SECONDS.toMillis(value));
        }

        /**
         * Sets the expiration timeout unit to minutes.
         * <p>
         * This method follows a with().
         */
        public ExpectEvaluator minutes() {
            return new ExpectEvaluator(TimeUnit.MINUTES.toMillis(value));
        }

        /**
         * Sets the expiration timeout unit to hours.
         * <p>
         * This method follows a with().
         */
        public ExpectEvaluator hours() {
            return new ExpectEvaluator(TimeUnit.HOURS.toMillis(value));
        }

        /**
         * Sets the expiration timeout unit to days.
         * <p>
         * This method follows a with().
         */
        public ExpectEvaluator days() {
            return new ExpectEvaluator(TimeUnit.DAYS.toMillis(value));
        }
    }

    public static final class ExpectEvaluator {
        final long milliseconds;

        private ExpectEvaluator(long milliseconds) {
            this.milliseconds = milliseconds;
        }

        /**
         * Assigns an initial value and an operator that updates any existing value initially and after expiry.
         *
         * @param initialValue This is used for the first call to the operator.
         * @param eval         This gets evaluated first and after each timeout
         * @param <T>          The cached type
         * @return The cache instance. This can be used now.
         */
        public <T> Cached<T> with(@Nullable T initialValue, UnaryOperator<T> eval) {
            return new TimedCache<T>(milliseconds, initialValue, eval, true);
        }

        /**
         * Initially and after each expiry, a new value is calculated via this Callable instance.
         * Expired values will be discarded completely.
         *
         * @param eval This gets evaluated first and after each timeout
         * @param <T>  The cached type
         * @return The cache instance. This can be used now.
         */
        public <T> Cached<T> with(Supplier<T> eval) {
            return with(null, v -> eval.get());
        }

        /**
         * Assigns an initial value and an operator that updates any existing value initially and after expiry.
         * Calculated value may be null.
         *
         * @param initialValue This is used for the first call to the operator.
         * @param eval         This gets evaluated first and after each timeout
         * @param <T>          The cached type
         * @return The cache instance. This can be used now.
         */
        public <T> Cached<T> withNullable(@Nullable T initialValue, UnaryOperator<T> eval) {
            return new TimedCache<T>(milliseconds, initialValue, eval, false);
        }

        /**
         * Initially and after each expiry, a new value is calculated via this Callable instance.
         * Expired values will be discarded completely.
         * Calculated value may be null.
         *
         * @param eval This gets evaluated first and after each timeout
         * @param <T>  The cached type
         * @return The cache instance. This can be used now.
         */
        public <T> Cached<T> withNullable(Supplier<T> eval) {
            return withNullable(null, v -> eval.get());
        }
    }

    private volatile boolean isInitialized;
    private volatile T instance;
    private final UnaryOperator<T> evaluator;
    private final boolean mandatory;
    private final Semaphore updateResource = new Semaphore(1);

    private Cached(Supplier<T> eval, boolean mandatory) {
        this.evaluator = x -> eval.get();
        this.mandatory = mandatory;
    }

    private Cached(T initialValue, UnaryOperator<T> evaluator, boolean mandatory) {
        this.evaluator = evaluator;
        this.mandatory = mandatory;
        instance = initialValue;
    }

    /**
     * Gets the cached value.
     * <p>
     * Update the cached value, if refresh rate has expired.
     */
    @Override
    public T get() {
        T value = instance;
        if (neededRefresh()) {
            return instance;
        }

        return value;
    }

    abstract boolean isValid();

    private boolean neededRefresh() {
        if (isInitialized && isValid()) {
            return false;
        }
        tryRefresh();
        return true;
    }

    /**
     * Refreshes the value to an updated instance.
     * This either starts the refresh process immediately, or it waits until another updating thread has finished.
     */
    public void refresh() {
        isInitialized = false;
        if (!tryRefresh()) {
            spinWait();
        }
    }

    private boolean tryRefresh() {
        if (updateResource.tryAcquire()) {
            try {
                T value;
                try {
                    value = evaluator.apply(instance);
                } catch (RefreshNotPossibleException ex) {
                    if (instance == null && mandatory) {
                        throw new IllegalStateException("Evaluator " + evaluator + " could not refresh on initial run", ex);
                    }
                    return true;
                }
                if (value == null && mandatory) {
                    throw new NullPointerException("Evaluator " + evaluator + " returned null value");
                }
                isInitialized = true;
                instance = value;
                postRefresh();
            } finally {
                updateResource.release();
            }

            return true;
        } else {
            // Another thread is updating - only wait for it if no old value is available or refresh is explicitly requested
            waitIfUninitialized();
        }

        return false;
    }

    void postRefresh() {}

    private void waitIfUninitialized() {
        while (!isInitialized) {
            spinWait();
        }
    }

    private void spinWait() {
        try {
            updateResource.acquire();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Thread is interrupted, " + evaluator + " may return uninitialized null value.");
        }
        updateResource.release();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (!isInitialized) sb.append("Uninitialized ");
        sb.append(getClass().getSimpleName());
        if (isInitialized) {
            sb.append(" (").append(instance).append(')');
        }
        postToString(sb);
        return sb.toString();
    }

    void postToString(StringBuilder sb) {}

    /**
     * Assigned Operators can throw this to indicate that a refresh is not possible yet,
     * and the cached value shall remain until it's possible again.
     *
     * If this is thrown on initial calculation, then either an {@link IllegalStateException} is thrown if the
     * result is mandatory, or null is returned if the result is nullable.
     */
    public static class RefreshNotPossibleException extends RuntimeException {
        private static final long serialVersionUID = 5734137134481666718L;
    }

    private static final class TimedCache<T> extends Cached<T> {
        private volatile long lastUpdate;
        private final long refreshRate;

        private TimedCache(long refreshRate, T initialValue, UnaryOperator<T> evaluator, boolean mandatory) {
            super(initialValue, evaluator, mandatory);
            this.refreshRate = refreshRate;
        }

        @Override
        boolean isValid() {
            return System.currentTimeMillis() - lastUpdate <= refreshRate;
        }

        @Override
        void postRefresh() {
            super.postRefresh();
            lastUpdate = System.currentTimeMillis();
        }

        @Override
        void postToString(StringBuilder sb) {
            super.postToString(sb);
            if (isValid()) {
                sb.append("; expires in ").append(Strings.printDuration(
                        TimeUnit.MILLISECONDS.toNanos(refreshRate + lastUpdate - System.currentTimeMillis()), TimeUnit.MILLISECONDS));
            } else {
                sb.append("; expired since ").append(Strings.printDuration(
                        TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis() - refreshRate - lastUpdate), TimeUnit.MILLISECONDS));
            }
        }
    }

    private static final class OneTimeInitializer<T> extends Cached<T> {
        private OneTimeInitializer(Supplier<T> evaluator, boolean mandatory) {
            super(evaluator, mandatory);
        }

        @Override
        boolean isValid() {
            return true;
        }
    }

    private static final class ImmediateUpdater<T> extends Cached<T> {
        private ImmediateUpdater(T initialValue, UnaryOperator<T> evaluator, boolean mandatory) {
            super(initialValue, evaluator, mandatory);
        }

        @Override
        boolean isValid() {
            return false;
        }
    }
}
