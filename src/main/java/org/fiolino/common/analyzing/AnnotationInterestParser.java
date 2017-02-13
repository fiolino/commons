package org.fiolino.common.analyzing;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.fiolino.common.container.Container;
import org.fiolino.common.processing.Analyzer;
import org.fiolino.common.processing.ModelDescription;
import org.fiolino.common.processing.ValueDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Created by kuli on 01.12.15.
 */
public class AnnotationInterestParser {

  private static final Logger logger = LoggerFactory.getLogger(AnnotationInterestParser.class);

  private static class InterestEnvironment {
    private final String name;
    private final Set<AnalyzedElement> elements;
    final MethodHandle action;

    InterestEnvironment(String name, Set<AnalyzedElement> elements, MethodHandle action) {
      this.name = name;
      this.elements = elements;
      this.action = action;
    }

    Consumer<Field> toFieldAction(final Object analyzeable, final Analyzer analyzer) {
      if (!elements.contains(AnalyzedElement.FIELD)) {
        return f -> {};
      }
      return f -> {
          if (logger.isDebugEnabled()) {
            logger.debug("Calling " + name + " for field " + f);
          }
          Object child = execute(analyzeable, analyzer, () -> analyzer.getModelDescription().getValueDescription(f), f, null);
          if (child == null || child == analyzeable) {
            return;
          }
          analyzer.analyzeAgain(child);
      };
    }

    Consumer<Method> toMethodAction(final Object analyzeable, final Analyzer analyzer) {
      if (!elements.contains(AnalyzedElement.METHOD)) {
        return m -> {};
      }
      return m -> {
          if (logger.isDebugEnabled()) {
            logger.debug("Calling " + name + " for method " + m);
          }
          Object child = execute(analyzeable, analyzer, () -> analyzer.getModelDescription().getValueDescription(m), null, m);
          if (child == null || child == analyzeable) {
            return;
          }
          analyzer.analyzeAgain(child);
      };
    }

    protected Object execute(Object analyzeable, Analyzer analyzer,
                             Supplier<ValueDescription> valueDescriptionSupplier,
                             Field f, Method m)
        throws ModelInconsistencyException {
      ValueDescription valueDescription = valueDescriptionSupplier.get();
      try {
        return action.invokeExact(analyzeable, analyzer, valueDescription, valueDescription.getConfiguration(), f, m);
      } catch (RuntimeException | Error e) {
        throw e;
      } catch (Throwable t) {
        throw new RuntimeException("Error executing " + action + " for " + (f == null ? m : f), t);
      }
    }
  }

  private static class AnnotationInterestEnvironment extends InterestEnvironment {
    private final Class<? extends Annotation> annotationType;

    AnnotationInterestEnvironment(String name, Set<AnalyzedElement> elements,
                                  Class<? extends Annotation> annotationType, MethodHandle action) {
      super(name, elements, action);
      this.annotationType = annotationType;
    }

    @Override
    protected Object execute(Object analyzeable, Analyzer analyzer,
                             Supplier<ValueDescription> valueDescriptionSupplier,
                             Field f, Method m) {
      AccessibleObject accessible = f == null ? m : f;
      Annotation anno = accessible.getAnnotation(annotationType);
      if (anno == null) {
        return null;
      }
      ValueDescription valueDescription = valueDescriptionSupplier.get();
      try {
        return action.invokeExact(analyzeable, analyzer, valueDescription, valueDescription.getConfiguration(),
                f, m, anno);
      } catch (RuntimeException | Error e) {
        throw e;
      } catch (Throwable t) {
        throw new RuntimeException("Error executing " + action + " for " + accessible, t);
      }
    }
  }

  private final Class<?> parsedType;
  private final List<InterestEnvironment> handlers = new ArrayList<>();

  private class MethodVisitor implements Consumer<Method> {
    private final Object toParse;
    private final Priority priority;

    MethodVisitor(Object toParse, Priority priority) {
      this.toParse = toParse;
      this.priority = priority;
    }

    @Override
    public void accept(Method method) {
      visitMethod(toParse, method, priority);
    }
  }

  public AnnotationInterestParser(Object toParse) {
    parsedType = toParse.getClass();
    ClassWalker<RuntimeException> walker = new ClassWalker<>();
    walker.onMethod(new MethodVisitor(toParse, Priority.INITIALIZING));
    walker.onMethod(new MethodVisitor(toParse, Priority.PREPROCESSING));
    walker.onMethod(new MethodVisitor(toParse, Priority.PROCESSING));
    walker.onMethod(new MethodVisitor(toParse, Priority.POSTPROCESSING));
    walker.analyze(parsedType);
  }

  private void visitMethod(Object toParse, Method method, Priority priority) {
    AnnotationInterest annotation = method.getAnnotation(AnnotationInterest.class);
    if (annotation == null || annotation.value() != priority) {
      return;
    }

    MethodHandles.Lookup lookup;
    if (toParse instanceof Analyzeable) {
      lookup = ((Analyzeable) toParse).getLookup();
    } else {
      lookup = publicLookup();
    }
    lookup = lookup.in(toParse.getClass());

    MethodHandle handle;
    try {
      handle = lookup.unreflect(method);
    } catch (IllegalAccessException ex) {
      logger.warn("Method " + method + " is not accessible!");
      return;
    }
    if (Modifier.isStatic(method.getModifiers())) {
      handle = MethodHandles.dropArguments(handle, 0, Object.class);
    } else {
      handle = handle.asType(handle.type().changeParameterType(0, Object.class));
    }
    handle = ensureCorrectReturnType(handle);

    Set<AnalyzedElement> elements = EnumSet.noneOf(AnalyzedElement.class);
    Collections.addAll(elements, annotation.elements());
    Class<? extends Annotation> annotationType = annotation.annotation();
    Class<?>[] parameterTypes = method.getParameterTypes();
    int n = parameterTypes.length;
    int[] parameterIndexes = new int[n + 1];

    for (int i = 0; i < n; ) {
      Class<?> pClass = parameterTypes[i++];
      if (Analyzer.class.equals(pClass)) {
        parameterIndexes[i] = 1;
      } else if (ValueDescription.class.equals(pClass)) {
        parameterIndexes[i] = 2;
      } else if (Container.class.equals(pClass)) {
        parameterIndexes[i] = 3;
      } else if (pClass.isAnnotation()) {
        parameterIndexes[i] = 6;
        if (Annotation.class.equals(pClass)) {
          if (annotationType.equals(Annotation.class)) {
            throw new AssertionError("Method " + method + " has abstract Annotation type and must therefore specify which to use.");
          }
        } else {
          Class<? extends Annotation> concreteType = pClass.asSubclass(Annotation.class);
          if (annotationType.equals(Annotation.class) || annotationType.equals(concreteType)) {
            annotationType = concreteType;
          } else {
            throw new AssertionError("Cannot show interest for " + annotationType.getName() + " and " + concreteType.getName() + " together.");
          }
          handle = handle.asType(handle.type().changeParameterType(i, Annotation.class));
        }
      } else if (Field.class.equals(pClass)) {
        elements.add(AnalyzedElement.FIELD);
        parameterIndexes[i] = 4;
      } else if (Method.class.equals(pClass)) {
        elements.add(AnalyzedElement.METHOD);
        parameterIndexes[i] = 5;
      } else {
        throw new AssertionError("Method " + method + "'s parameter #" + i + " is of unhandled type " + pClass.getName());
      }
    }

    if (elements.isEmpty()) {
      elements = EnumSet.allOf(AnalyzedElement.class);
    }
    InterestEnvironment env;
    if (annotationType.equals(Annotation.class)) {
      if (logger.isDebugEnabled()) {
        logger.debug("Method " + method + " will be called for all " + logInfo(elements));
      }
      handle = MethodHandles.permuteArguments(handle, methodType(Object.class, Object.class, Analyzer.class,
              ValueDescription.class, Container.class, Field.class, Method.class), parameterIndexes);
      env = new InterestEnvironment(method.toGenericString(), elements, handle);
    } else {
      if (logger.isDebugEnabled()) {
        logger.debug("Method " + method + " will be called for all " + logInfo(elements) + " annotated with "
                + annotationType.getName());
      }
      handle = MethodHandles.permuteArguments(handle, methodType(Object.class, Object.class, Analyzer.class,
              ValueDescription.class, Container.class, Field.class, Method.class, Annotation.class), parameterIndexes);
      env = new AnnotationInterestEnvironment(method.toGenericString(), elements, annotationType, handle);
    }
    handlers.add(env);
  }

  private String logInfo(Set<AnalyzedElement> elementTypes) {
    if (elementTypes.size() > 1) {
      return "accessible objects";
    } else {
      return elementTypes.iterator().next().name().toLowerCase() + "s";
    }
  }

  private MethodHandle ensureCorrectReturnType(MethodHandle handle) {
    Class<?> returnType = handle.type().returnType();
    if (returnType == void.class) {
      // Then return the same instance
//            Class<?>[] parameterTypes = handle.type().parameterArray();
//            Class<?>[] dropAllExceptFirst = new Class<?>[parameterTypes.length - 1];
//            System.arraycopy(parameterTypes, 1, dropAllExceptFirst, 0, dropAllExceptFirst.length);
//            return MethodHandles.foldArguments(MethodHandles.dropArguments(MethodHandles.identity(Object.class), 1, dropAllExceptFirst), handle);

      return MethodHandles.filterReturnValue(handle, MethodHandles.constant(Object.class, null));
    } else {
      return handle.asType(handle.type().changeReturnType(Object.class));
    }
  }

  public void analyze(Object analyzeable, Analyzer analyzer) throws ModelInconsistencyException {
    if (!parsedType.isInstance(analyzeable)) {
      throw new IllegalArgumentException("Parser had analyzed " + parsedType.getName()
              + ", but argument is " + analyzeable);
    }
    ModelDescription modelDescription = analyzer.getModelDescription();
    if (analyzeable instanceof Analyzeable) {
      ((Analyzeable) analyzeable).preAnalyze(modelDescription);
    }
    ClassWalker<ModelInconsistencyException> classWalker = new ClassWalker<>();
    for (InterestEnvironment env : handlers) {
      // classAnalyzer.forClasses()
      classWalker.onField(env.toFieldAction(analyzeable, analyzer));
      classWalker.onMethod(env.toMethodAction(analyzeable, analyzer));
    }
    classWalker.analyze(analyzer.getModelDescription().getModelType());
    if (analyzeable instanceof Analyzeable) {
      ((Analyzeable) analyzeable).postAnalyze(modelDescription);
    }
  }
}
