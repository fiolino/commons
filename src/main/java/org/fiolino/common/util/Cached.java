package org.fiolino.common.util;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

/**
 * This class can be used to hold values that usually don't change,
 * but should be frequently updated to accept changes if there are some.
 * <p>
 * This is best used for tasks that need some resources, but not that many
 * that frequent updates would hurt. An example is some data read from the
 * file system.
 * <p>
 * Example:
 * Cached<DataObject> valueHolder = Cached.updateEvery(5).hours().with(new Callable<DataObject>() {...});
 *
 * @author kuli
 */
public final class Cached<T> {

  /**
   * This is the starting factory method, followed by a method for the time unit.
   *
   * @param value How many time units shall the cache keep its value.
   */
  public static ExpectUnit updateEvery(long value) {
    return new ExpectUnit(value);
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
    public <T> Cached<T> with(T initialValue, UnaryOperator<T> eval) {
      return new Cached<T>(milliseconds, initialValue, eval);
    }

    /**
     * Initially and after each expiry, a new value is calculated via this Callable instance.
     * Expired values will be discarded completely.
     *
     * @param eval This gets evaluated first and after each timeout
     * @param <T>  The cached type
     * @return The cache instance. This can be used now.
     */
    public <T> Cached<T> with(Callable<T> eval) {
      return with(null, v -> {
        try {
          return eval.call();
        } catch (RuntimeException | Error e) {
          throw e;
        } catch (Throwable ex) {
          throw new UndeclaredThrowableException(ex, "Cannot calculate value " + eval);
        }
      });
    }
  }

  private volatile boolean isInitialized;
  private volatile T instance;
  private volatile long lastUpdate;
  private final long refreshRate;
  private final UnaryOperator<T> evaluator;
  private final Semaphore updateResource = new Semaphore(1);

  private Cached(long refreshRate, T initialValue, UnaryOperator<T> evaluator) {
    this.instance = initialValue;
    this.refreshRate = refreshRate;
    this.evaluator = evaluator;
  }

  /**
   * Gets the cached value.
   * <p>
   * Update the cached value, if refresh rate has expired.
   */
  public T get() {
    T value;
    do {
      value = instance;
    } while (neededRefresh());

    return value;
  }

  private boolean isValid() {
    return System.currentTimeMillis() - lastUpdate <= refreshRate;
  }

  private boolean neededRefresh() {
    // Unsafe.loadFence() -- then lastUpdate could be non-volatile
    if (isValid()) {
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
          return true;
        }
        if (value == null) {
          throw new NullPointerException("Evaluator " + evaluator + " returned null value");
        }
        lastUpdate = System.currentTimeMillis();
        isInitialized = true;
        instance = value;
      } finally {
        updateResource.release();
      }

      return true;
    } else {
      waitIfUninitialized();
    }

    return false;
  }

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

  public static class RefreshNotPossibleException extends RuntimeException {
    private static final long serialVersionUID = 5734137134481666718L;

  }
}
