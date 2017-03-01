package org.fiolino.common.reflection;

/**
 * Use this to dynamically create lambda functions based on additional parameters.
 * <p>
 * Created by Kuli on 18/11/2016.
 */
public interface LambdaGenerator<I> {
    /**
     * Creates the lambda.
     *
     * @param parameters Must match the additional parameters given im lambdafy().
     * @return the new lambda instance
     */
    I create(Object... parameters);
}
