package org.fiolino.common.ioc;

/**
 * This exception is thrown if an {@link FactoryFinder} is asked for a provider
 * where it can't find an accessible factory handle with the given parameters.
 */
public class NoSuchProviderException extends RuntimeException {

    private static final long serialVersionUID = -5416040085073505996L;

    private final Class<?> returnType;
    private final Class<?>[] parameterTypes;

    public NoSuchProviderException(Class<?> returnType, Class<?>[] parameterTypes) {
        this.returnType = returnType;
        this.parameterTypes = parameterTypes;
    }

    public NoSuchProviderException(Class<?> returnType, Class<?>[] parameterTypes, String message) {
        super(message);
        this.returnType = returnType;
        this.parameterTypes = parameterTypes;
    }

    public NoSuchProviderException(Class<?> returnType, Class<?>[] parameterTypes, String message, Throwable cause) {
        super(message, cause);
        this.returnType = returnType;
        this.parameterTypes = parameterTypes;
    }

    public NoSuchProviderException(Class<?> returnType, Class<?>[] parameterTypes, Throwable cause) {
        super(cause);
        this.returnType = returnType;
        this.parameterTypes = parameterTypes;
    }

    /**
     * This type was asked as the return type, i.e. such a type should have been instantiated or converted to.
     */
    public Class<?> getReturnType() {
        return returnType;
    }

    /**
     * These types were asked as parameter types.
     *
     * @return No defensive copy, it's informational only anyway
     */
    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }
}
