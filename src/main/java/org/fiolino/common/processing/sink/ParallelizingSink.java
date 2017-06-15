package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A sink that splits the targets into parallel threads.
 * <p>
 * Created by kuli on 31.03.16.
 */
public final class ParallelizingSink<T> extends ChainedSink<T, T> {
    private static final Logger logger = Logger.getLogger(ParallelizingSink.class.getName());

    private final Consumer<Runnable> executor;
    private final String name;
    private final int parallelity;
    private Task next;
    private volatile int commitCount;
    private long timeout = TimeUnit.MINUTES.toSeconds(5);
    private volatile Container currentMetadata;

    /**
     * Creates a parallelizer.
     *
     * @param target      This will be parallelized. It must be either a {@link CloneableSink} or a {@link ThreadsafeSink}.
     * @param name        The name of the sink, used for the logger.
     * @param parallelity How many threads are running of the target sink. 0 means no parallelity at all,
     *                    the target will be returned directly. -1 means the parallelity is based on the number
     *                    of cores, i.e. it's the number of cores minus one.
     * @param queueSize   The size of the queue for each thread. When the queue is full, then the producer thread first
     *                    tries to find another thread's queue, and waits until the next active queue gets freed by
     *                    at least one.
     * @param <T>         The type of the processed items.
     * @return The {@link ParallelizingSink}, or the target if parallelity is zero.
     */
    public static <T> Sink<T> createFor(Sink<T> target, String name,
                                        Consumer<Runnable> executor,
                                        int parallelity, int queueSize) {
        validateTarget(target);
        int p = getRealParallelity(parallelity);
        if (p == 0) {
            return target;
        }

        return new ParallelizingSink<>(target, name, executor, p, queueSize);
    }

    private static <T> void validateTarget(Sink<? super T> target) {
        if (!(target instanceof CloneableSink) && !(target instanceof ThreadsafeSink)) {
            throw new IllegalArgumentException("Target " + target + " must be either cloneable or thread safe!");
        }
    }

    private static int getRealParallelity(int parallelity) {
        if (parallelity < 0) {
            return Runtime.getRuntime().availableProcessors() - 1;
        }
        return parallelity;
    }

    private ParallelizingSink(Sink<? super T> target, String name,
                              Consumer<Runnable> executor,
                              int parallelity, int queueSize) {
        super(target);
        this.name = name;
        assert parallelity >= 1;
        if (queueSize <= 0) {
            throw new IllegalArgumentException("QueueSize must be > 0: " + queueSize);
        }

        this.parallelity = parallelity;
        this.executor = executor;
        Task task = new Task(target, 1, queueSize);
        next = task;
        for (int i = 1; i < parallelity; i++) { // starting at 1 is intended, since one task is already created
            Sink<? super T> newTarget = targetForCloning(target);
            task = createTask(task, newTarget, queueSize);
        }
        task.setNext(next);
    }

    @Override
    public String toString() {
        return name;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    String nameFor(Task t) {
        return name + " #" + t.number + "/" + parallelity;
    }

    private Task createTask(Task task, Sink<? super T> target, int queueSize) {
        Task n = new Task(target, task.number + 1, queueSize);
        task.setNext(n);
        return n;
    }

    @Override
    public void accept(T value, Container metadata) throws Exception {
        currentMetadata = metadata;
        try {
            next = next.offer(value);
        } catch (InterruptedException ex) {
            logger.log(Level.WARNING, () -> "Adding " + value + " to full queue was interrupted!");
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void commit(Container metadata) throws Exception {
        try {
            SynchronizationPoint p = new SynchronizationPoint(timeout, metadata);
            next.putIntoAll(p);
            p.startAndWait(name + " <MAIN THREAD>");
            commitCount++;
        } catch (InterruptedException ex) {
            logger.log(Level.WARNING, () -> "Interrupted while finishing!");
            Thread.currentThread().interrupt();
        }
        next.throwError();
        super.commit(metadata);
    }

    public int[] getWorkCounters() {
        return next.getCounters();
    }

    private static class SynchronizationPoint {
        private final CountDownLatch initializer;
        private final long timeout;
        private final Container metadata;
        private volatile CountDownLatch latch;
        private int waiters;

        SynchronizationPoint(long timeout, Container metadata) {
            this.timeout = timeout;
            this.metadata = metadata;
            initializer = new CountDownLatch(1);
            waiters = 1;
        }

        Container getMetadata() {
            return metadata;
        }

        void register() {
            if (waiters == -1) {
                throw new IllegalStateException("Was already started.");
            }
            waiters++;
        }

        /**
         * This is called from the main thread.
         *
         * @param name The name is only used in case of timeout
         * @throws InterruptedException If the synchronization point wasn't added to the queues
         */
        void startAndWait(String name) throws InterruptedException {
            latch = new CountDownLatch(waiters);
            logger.info(() -> "Will wait for " + waiters + " threads");
            waiters = -1;
            initializer.countDown();
            await(name);
        }

        /**
         * This is called from the individual tasks.
         *
         * @param name Used for logging
         * @throws InterruptedException If the synchronization was interrupted
         */
        void syncTask(String name) throws InterruptedException {
            logger.info("Synchronizing " + name);
            initializer.await();
            await(name);
        }

        private void await(String name) throws InterruptedException {
            latch.countDown();
            if (latch.await(timeout, TimeUnit.SECONDS)) {
                return;
            }
            logger.log(Level.WARNING, () -> "Timeout after " + timeout + " seconds on " + name);
        }
    }

    private final class Task implements Runnable {
        private final int number;
        private final BlockingDeque<Object> queue;
        private final Sink<? super T> target;
        private int commitState = -1;
        private Task next;
        private int counter;
        private Throwable lastCause;

        Task(Sink<? super T> target, int number, int capacity) {
            this.number = number;
            this.queue = new LinkedBlockingDeque<>(capacity);
            this.target = target;
        }

        @Override
        public String toString() {
            return ParallelizingSink.this.toString() + " #" + number + "/" + parallelity;
        }

        void setNext(Task next) {
            this.next = next;
        }

        Task offer(T value) throws InterruptedException {
            return offerDirect(value, this);
        }

        private Task offerDirect(T value, Task originator) throws InterruptedException {
            ensureRunning();
            if (queue.offerLast(value)) {
                // There was space left, cool!
                return next;
            }
            // Try next task in a round-robin way
            return next.offerUntilBackAtStart(value, originator);
        }

        private Task offerUntilBackAtStart(T value, Task originator) throws InterruptedException {
            if (this == originator) {
                // All queues are full, so just wait until my own becomes available
                insertUnconditional(value);
                return next;
            }
            return offerDirect(value, originator);
        }

        private void ensureRunning() {
            if (!isRunning()) {
                commitState = commitCount;
                executor.accept(this);
            }
        }

        void putIntoAll(SynchronizationPoint sync) throws InterruptedException {
            putIntoMe(sync, this);
        }

        private void putIntoAll(SynchronizationPoint sync, Task originator) throws InterruptedException {
            if (this == originator) {
                return;
            }
            putIntoMe(sync, originator);
        }

        private void putIntoMe(SynchronizationPoint sync, Task originator) throws InterruptedException {
            if (isRunning()) {
                // No synchronization needed when it's not running
                sync.register();
                insertUnconditional(sync);
            }
            next.putIntoAll(sync, originator);
        }

        private boolean isRunning() {
            return commitState == commitCount;
        }

        private void insertUnconditional(Object elements) throws InterruptedException {
            queue.putLast(elements);
        }

        private void handle(Throwable t) {
            if (lastCause != null) {
                throwMultiException(t);
            }
            lastCause = t;
        }

        private void throwMultiException(Throwable t) {
            logger.log(Level.SEVERE, "Multiple exceptions in parallel tasks!", t);
        }

        void throwError() throws Exception {
            Throwable t = next.collect(lastCause, this);
            if (t == null) {
                return;
            }
            if (t instanceof Exception) {
                throw (Exception) t;
            }
            if (t instanceof Error) {
                throw (Error) t;
            }
            // This should never be reached
            throw new AssertionError(t);
        }

        private Throwable collect(Throwable t, Task originator) {
            Throwable my = lastCause;
            lastCause = null;
            if (originator == this) {
                return t;
            }
            if (t != null) {
                if (my != null) {
                    throwMultiException(my);
                }
                my = t;
            }
            return next.collect(my, originator);
        }

        @Override
        public void run() {
            setThreadName("evaluating...");
            try {
                Object next;
                do {
                    next = queue.pollFirst();
                } while (evaluate(next));
            } catch (Throwable t) {
                // This could only be catched if there's an exception within this parallelizer or a generic error elsewhere
                logger.log(Level.SEVERE, "Failed " + Thread.currentThread().getName(), t);
                run();
            }
            setThreadName("Finished.");
        }

        private void setThreadName(String work) {
            Thread.currentThread().setName(nameFor(this)
                    + " " + work + " Updates: " + counter + ", commits: " + commitState);
        }

        /**
         * Executes the next task.
         *
         * @param value What to do
         * @return true if there's more to come
         */
        private boolean evaluate(Object value) {
            try {
                while (value == null) {
                    // Nothing in my queue, so steal work from others
                    if (next.stealWorkFor(this)) {
                        return true;
                    }
                    // Nothing to do, so wait until new elements arrive
                    value = queue.takeFirst();
                }
                if (value instanceof SynchronizationPoint) {
                    while (next.stealWorkFor(this)) {
                        // Loop...
                    }
                    commit(((SynchronizationPoint) value).getMetadata());
                    ((SynchronizationPoint) value).syncTask(toString());
                    return false;
                }
                // Normal element
                @SuppressWarnings("unchecked")
                T item = (T) value;
                consume(item);
                return true;
            } catch (InterruptedException ex) {
                // Then let's finish
                logger.info("Thread " + Thread.currentThread().getName() + " is being stopped!");
                return false;
            }
        }

        private void commit(Container metadata) {
            if (target instanceof CloneableSink) {
                setThreadName("committing...");
                try {
                    ((CloneableSink<?, ?>) target).partialCommit(metadata);
                } catch (Exception ex) {
                    handle(ex);
                }
            }
        }

        private void consume(T value) {
            try {
                target.accept(value, currentMetadata);
            } catch (AssertionError e) {
                handle(e);
            } catch (Error e) {
                throw e;
            } catch (Throwable t) {
                handle(t);
            }
            counter++;
        }

        int[] getCounters() {
            int[] counters = new int[parallelity];
            setCounter(counters, 0);
            return counters;
        }

        private void setCounter(int[] counters, int index) {
            if (index >= parallelity) {
                return;
            }
            counters[index] = counter;
            next.setCounter(counters, index + 1);
        }

        /**
         * Tries to execute some work from other tasks.
         *
         * @param originator Who's doing the job
         * @return true if there was some work available
         * @throws InterruptedException If synchornization couldn't be pushed back (only theoretically)
         */
        private boolean stealWorkFor(Task originator) throws InterruptedException {
            if (this == originator) {
                // Then all queues are empty
                return false;
            }
            Object findNext = queue.pollFirst();
            if (findNext == null || !executeNextStolenWorkIn(findNext, originator)) {
                return next.stealWorkFor(originator);
            }
            return true;
        }

        private boolean executeNextStolenWorkIn(Object value, Task originator) throws InterruptedException {
            if (value instanceof SynchronizationPoint) {
                // It's a synchronization point, then better put it back.
                queue.putFirst(value);
                return false;
            }
            setThreadName("stealing work from " + number);
            @SuppressWarnings("unchecked")
            T item = (T) value;
            originator.consume(item);
            return true;
        }
    }

}
