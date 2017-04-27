package org.fiolino.common.reflection.dynamic;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.NamingStrategy;
import net.bytebuddy.description.type.TypeDescription;

import java.util.regex.Pattern;

/**
 * Created by kuli on 19.04.17.
 */
public final class Namespace {
    private final String packageName;
    private final DynamicClassLoader classLoader;
    private final ByteBuddy byteBuddy;

    public static Namespace create(String packageName) {
        return create(Thread.currentThread().getContextClassLoader(), packageName);
    }

    public static Namespace create(ClassLoader classLoader, String packageName) {
        Pattern validPackage = Pattern.compile("^([\\p{L}_\\p{Sc}][\\p{L}\\p{N}_\\p{Sc}]*\\.)*[\\p{L}_\\p{Sc}][\\p{L}\\p{N}_\\p{Sc}]*$");
        if (!validPackage.asPredicate().test(packageName)) {
            throw new IllegalArgumentException("Invalid package name " + packageName);
        }
        return new Namespace(classLoader, packageName);
    }

    private Namespace(ClassLoader classLoader, String packageName) {
        this.packageName = packageName;
        this.classLoader = new DynamicClassLoader(classLoader);

        byteBuddy = new ByteBuddy().with(new MyNamingStrategy());
    }

    private class MyNamingStrategy extends NamingStrategy.AbstractBase {
        @Override
        protected String name(TypeDescription superClass) {
            String
            return packageName + '.' + superClass.getSimpleName() ;
        }
    }

    private static class DynamicClassLoader extends ClassLoader {
        DynamicClassLoader(ClassLoader parent) {
            super(parent);
        }
    }
}
