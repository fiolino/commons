package org.fiolino.common.ioc;

import org.fiolino.annotations.Scope;
import org.fiolino.annotations.ScopeSetter;
import org.fiolino.common.reflection.Methods;
import org.fiolino.common.reflection.Registry;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MutableCallSite;
import java.util.function.Supplier;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Defines the scope of the created DTO instances.
 */
public final class Scopes {
    /**
     * Local scope: Creates a new instance on every request.
     */
    public static final Class<? extends ScopeSetter> LOCAL = Local.class;

    /**
     * Creates one instance per thread.
     */
    public static final Class<? extends ScopeSetter> THREAD_LOCAL = ThreadLocal.class;

    /**
     * Global scope, but weakly referenced and refreshed on use if necessary.
     * No real thread safety is given; when a previous instance was wiped, multiple new instances could be created
     * when in concurrent access.
     * This should be used only for cases where instances could be recreated anyway. At best, they should be stateless.
     *
     * This scope can be useful in cases where the instance should be cached, but might be rarely used and so
     * can be garbage collected if free memory is needed.
     */
    public static final Class<? extends ScopeSetter> WEAK_REFERENCE = WeakReference.class;

    /**
     * Global scope: One singleton instance for all.
     * This is thread safe; it's guaranteed that only one instance will be created.
     *
     * This should be preferred to WEAK_REFERENCE as long as the created instance is not memory relevant.
     */
    public static final Class<? extends ScopeSetter> GLOBAL = Global.class;

    /**
     * Local scope: Creates a new instance on every request.
     */
    @Scope(Global.class)
    static class Local implements ScopeSetter {
        @Override
        public MethodHandle adjust(MethodHandle factory) {
            return factory;
        }
    }

    /**
     * Creates one instance per thread.
     */
    @Scope(Global.class)
    static class ThreadLocal implements ScopeSetter {
        @Override
        public MethodHandle adjust(MethodHandle factory) {
            @SuppressWarnings("unchecked")
            java.lang.ThreadLocal<?> local = java.lang.ThreadLocal.withInitial(Methods.lambdafy(factory, Supplier.class));
            return THREADLOCAL_GET.bindTo(local).asType(factory.type());
        }
    }

    /**
     * Global scope, but weakly referenced and refreshed on use if necessary.
     * No real thread safety is given; when a previous instance was wiped, multiple new instances could be created
     * when in concurrent access.
     * This should be used only for cases where instances could be recreated anyway. At best, they should be stateless.
     * 
     * This scope can be useful in cases where the instance should be cached, but might be rarely used and so
     * can be garbage collected if free memory is needed.
     */
    @Scope(Global.class)
    static class WeakReference implements ScopeSetter {
        @Override
        public MethodHandle adjust(MethodHandle factory) {
            CallSite callSite = WeakReferenceScope.prepareCallSite(factory);
            return callSite.dynamicInvoker();
        }
    }

    /**
     * Global scope: One singleton instance for all.
     * This is thread safe; it's guaranteed that only one instance will be created.
     * 
     * This should be preferred to WEAK_REFERENCE as long as the created instance is not memory relevant.
     */
    @Scope(GlobalFallback.class) // Necessary to avoid endless recursions
    static class Global implements ScopeSetter {
        @Override
        public MethodHandle adjust(MethodHandle factory) {
            return Registry.buildFor(factory).getAccessor();
        }
    }

    static class GlobalFallback implements ScopeSetter {
        @Override
        public MethodHandle adjust(MethodHandle factory) {
            return Registry.buildFor(factory).getAccessor();
        }
    }

    private static final MethodHandle THREADLOCAL_GET;
    
    static {
        try {
            THREADLOCAL_GET = publicLookup().findVirtual(java.lang.ThreadLocal.class, "get", methodType(Object.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new InternalError("ThreadLocal::get", ex);
        }
    }
    
    private static class WeakReferenceScope {
        private final CallSite callSite;
        private final MethodHandle factory;
        private volatile java.lang.ref.WeakReference<?> ref;
        
        static CallSite prepareCallSite(MethodHandle factory) {
            CallSite callSite = new MutableCallSite(methodType(Object.class));
            Lookup lookup = lookup();
            WeakReferenceScope myself = new WeakReferenceScope(callSite, factory);
            MethodHandle initial;
            try {
                initial = lookup.bind(myself, "runInitialization", methodType(Object.class));
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw new InternalError(ex);
            }
            callSite.setTarget(initial);
            return callSite;
        }
        
        private WeakReferenceScope(CallSite callSite, MethodHandle factory) {
            this.callSite = callSite;
            this.factory = factory;
        }
        
        @SuppressWarnings("unused")
        private synchronized Object runInitialization() throws Throwable {
            if (ref != null) {
                // Could be in concurrent access
                return get();
            }
            Object value = setRef();
            MethodHandle furtherCalls = lookup().bind(this, "get", methodType(Object.class));
            callSite.setTarget(furtherCalls);
            return value;
        }
        
        private Object setRef() throws Throwable {
            Object value = factory.invokeExact();
            ref = new java.lang.ref.WeakReference<>(value);
            return value;
        }
        
        private Object get() throws Throwable {
            Object v = ref.get();
            if (v != null) {
                return v;
            }
            return setRef();
        }
    }
}
