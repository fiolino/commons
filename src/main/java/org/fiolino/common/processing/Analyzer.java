package org.fiolino.common.processing;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import org.fiolino.common.analyzing.AnnotationInterestParser;

/**
 * The analyzer is used to process all annotated fields on a target model.
 * <p>
 * Created by kuli on 17.12.15.
 */
public class Analyzer extends AbstractModelDescriptionContainer {

  private static final Map<Class<?>, WeakReference<AnnotationInterestParser>> parsers = new HashMap<>();

  private final AnnotationInterestParser parser;

  private Analyzer(ModelDescription modelDescription, AnnotationInterestParser parser) {
    super(modelDescription);
    this.parser = parser;
  }

  /**
   * Analyzes all analyzeable instances for the given model type.
   */
  public static void analyzeAll(ModelDescription description, Object... analyzeable) {
    for (Object o : analyzeable) {
      Analyzer a = createSingleAnalyzer(description, o);
      a.analyzeAgain(o);
    }
  }

  /**
   * Analyzes another instance of the same analyzeable type for the same model type.
   */
  public void analyzeAgain(Object analyzeable) {
    parser.analyze(analyzeable, this);
  }

  /**
   * Analyzes another instance of the same analyzeable type for another model type.
   */
  public void analyze(ModelDescription newDescription, Object analyzeable) {
    new Analyzer(newDescription, parser).analyzeAgain(analyzeable);
  }

  private static Analyzer createSingleAnalyzer(ModelDescription modelDescription, Object analyzeable) {
    Class<?> key = analyzeable.getClass();
    WeakReference<AnnotationInterestParser> ref = parsers.get(key);
    AnnotationInterestParser parser;
    if (ref == null || (parser = ref.get()) == null) {
      parser = new AnnotationInterestParser(analyzeable);
      ref = new WeakReference<>(parser);
      parsers.put(key, ref);
    }
    return new Analyzer(modelDescription, parser);
  }

}
