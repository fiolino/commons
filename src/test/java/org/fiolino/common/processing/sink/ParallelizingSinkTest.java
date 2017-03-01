package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;
import org.fiolino.common.util.Strings;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Created by kuli on 06.04.16.
 */
public class ParallelizingSinkTest {

    private static final int[] KNOWN_PRIMS;
    private static final int NUMBER_OF_WALKTHROUGHS = 2;

    static {
        int[] prims = new int[100000];
        int i = 0;
        InputStream primStream = ParallelizingSinkTest.class.getResourceAsStream("/prims.lst");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(primStream))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] numbers = line.split(",");
                for (String n : numbers) {
                    n = n.trim();
                    if (n.equals("")) {
                        continue;
                    }
                    int p = Integer.parseInt(n);
                    prims[i++] = p;
                }
            }
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }

        KNOWN_PRIMS = Arrays.copyOf(prims, i);
    }

    private final Executor executor = Executors.newCachedThreadPool(new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            int c = counter.getAndIncrement();
            if (c > 100) {
                throw new AssertionError();
            }
            return new Thread(r, "Test #" + c);
        }
    });

    @Test
    public void testMultipleParallelity() throws Exception {
        for (int w = 1; w <= NUMBER_OF_WALKTHROUGHS; w++) {
            for (int parallelity = 1; parallelity < 16; parallelity++) {
                MySink<List<Integer>> prims = new MySink<>();
                Sink<Integer> sink = createParallelSink(parallelity, prims);
                long start = System.nanoTime();
                feedSinkUpTo(sink, 1000000);
                long duration = System.nanoTime() - start;
                if (w == NUMBER_OF_WALKTHROUGHS) {
                    System.out.println("With parallelity of " + parallelity + ", it took "
                            + Strings.printDuration(duration, TimeUnit.NANOSECONDS));
                    if (sink instanceof ParallelizingSink) {
                        int[] counters = ((ParallelizingSink) sink).getWorkCounters();
                        System.out.println("Work load: " + Arrays.toString(counters));
                    }
                }

                validateResult(prims);
            }
        }
    }

    private void validateResult(MySink<List<Integer>> prims) {
        List<Integer> calculated = prims.result;
        Collections.sort(calculated);

        int i = 0;
        for (Integer c : calculated) {
            int p = KNOWN_PRIMS[i++];
            assertEquals("Check expected prim " + p + " with calculated " + c + " at pos #" + i, p, c.intValue());
        }
    }

    private void feedSinkUpTo(Sink<Integer> stoppableSink, int max) throws Exception {
        int num = 2;
        while (num < max) {
            stoppableSink.accept(num++, Container.empty());
        }

        stoppableSink.commit(Container.empty());
    }

    private Sink<Integer> createParallelSink(int parallelity, MySink<List<Integer>> prims) {
        ThreadsafeSink<List<Integer>> primCollector = new MultiAggregatingSink<>(prims, KNOWN_PRIMS.length + 1);
        Sink<Integer> aggregator = new AggregatingSink<>(primCollector, KNOWN_PRIMS.length + 1);
        FilteringSink<Integer> primChecker = new FilteringSink<>(aggregator, value -> {
            // Very simple algorithm...
            for (int n = 2; n * n <= value; n++) {
                if ((value % n) == 0) {
                    return false;
                }
            }

            return true;
        });
        Sink<List<Integer>> decomposer = new DecomposingSink<>(primChecker);
        Sink<List<Integer>> parallel = ParallelizingSink.createFor(decomposer, "Test",
                executor::execute, parallelity, 5000);
        return new AggregatingSink<>(parallel, 1000);
    }

    private static class Logger extends ChainedSink<List<Integer>, List<Integer>> implements CloneableSink<List<Integer>, Logger> {
        public Logger(Sink<? super List<Integer>> target) {
            super(target);
        }

        @Override
        public void partialCommit(Container metadata) throws Exception {
            if (getTarget() instanceof CloneableSink)
                ((CloneableSink) getTarget()).partialCommit(metadata);
        }

        @Override
        public Logger createClone() {
            return new Logger(targetForCloning());
        }

        @Override
        public void accept(List<Integer> value, Container metadata) throws Exception {
            System.out.println(value.get(0));
            getTarget().accept(value, metadata);
        }
    }

    @Test
    public void testGrowingFeed() throws Exception {
        MySink<List<Integer>> prims = new MySink<>();
        Sink<Integer> sink = createParallelSink(4, prims);
        for (int max = 3; max < 10000; max++) {
            feedSinkUpTo(sink, max);
            validateResult(prims);
        }
    }

    private static class MyException extends Exception {
    }

    private static class ExceptionThrower implements CloneableSink<Object, ExceptionThrower> {
        @Override
        public void accept(Object value, Container metadata) throws Exception {
            if ("Bang!".equals(value)) {
                throw new MyException();
            }
        }

        @Override
        public void commit(Container metadata) throws Exception {
            // Does nothing
        }

        @Override
        public void partialCommit(Container metadata) throws Exception {
            // Does nothing
        }

        @Override
        public ExceptionThrower createClone() {
            return new ExceptionThrower();
        }
    }

    @Test
    public void testException() throws Exception {
        ExceptionThrower thrower = new ExceptionThrower();
        Sink<Object> sink = ParallelizingSink.createFor(thrower, "Test Exception", executor::execute, 4, 10);
        Sink<List<Object>> listSink = new DecomposingSink<>(sink);
        Object[] array = new Object[1000];
        Arrays.fill(array, "harmless");
        array[587] = "Bang!";
        List<Object> list = Arrays.asList(array);
        listSink.accept(list, Container.empty());

        try {
            listSink.commit(Container.empty());
        } catch (MyException ex) {
            // All fine
            return;
        }
        fail("Exception was expected");
    }
}
