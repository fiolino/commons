package org.fiolino.common.processing;

import org.fiolino.common.util.Instantiator;

import java.util.function.Supplier;

/**
 * Created by kuli on 08.04.15.
 */
public class BeanCreator<S, T> implements ValueSupplier<S, T> {
    private final Supplier<? extends T> factory;
    private Processor<S, T> processor;

    public static <S, T> BeanCreator<S, T> on(Class<? extends T> modelType) {
        return new BeanCreator<>(modelType);
    }

    public static <S, T> BeanCreator<S, T> using(Supplier<? extends T> factory) {
        return new BeanCreator<>(factory);
    }

    public static <S, T> BeanCreator<S, T> using(Supplier<? extends T> factory, Processor<S, T> processor) {
        return new BeanCreator<>(factory, processor);
    }

    protected BeanCreator(Supplier<? extends T> factory) {
        this(factory, Processor.doNothing());
    }

    protected BeanCreator(Supplier<? extends T> factory, Processor<S, T> processor) {
        this.factory = factory;
        this.processor = processor;
    }

    protected BeanCreator(Class<? extends T> modelType) {
        this(Instantiator.creatorFor(modelType));
    }

    protected T newInstance() {
        return factory.get();
    }

    protected void preInitialize(S source, T instance) {
        // Do nothing by default
    }

    protected void postInitialize(S source, T instance) {
        // Do nothing by default
    }

    @Override
    public final T getFor(S source) throws Exception {
        T newInstance = newInstance();
        preInitialize(source, newInstance);
        processor.process(source, newInstance);
        postInitialize(source, newInstance);
        return newInstance;
    }

    public final void register(Processor<? super S, ? super T> another) {
        processor = processor.andThen(another);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " creates " + factory + " with " + processor;
    }
}
