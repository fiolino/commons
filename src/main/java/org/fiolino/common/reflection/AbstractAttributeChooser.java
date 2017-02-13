package org.fiolino.common.reflection;

/**
 * Created by Kuli on 6/17/2016.
 */
abstract class AbstractAttributeChooser implements AttributeChooser {

  private abstract static class UnaryOperator extends AbstractAttributeChooser {
    final AttributeChooser assignment;

    UnaryOperator(AttributeChooser assignment) {
      this.assignment = assignment;
    }
  }

  private static class Complement extends UnaryOperator {
    Complement(AttributeChooser assignment) {
      super(assignment);
    }

    @Override
    public boolean accepts(String name, Class<?> type, AnnotationProvider annotationProvider) {
      return !assignment.accepts(name, type, annotationProvider);
    }
  }

  private abstract static class BinaryOperator extends UnaryOperator {
    final AttributeChooser secondAssignment;

    BinaryOperator(AttributeChooser assignment, AttributeChooser secondAssignment) {
      super(assignment);
      this.secondAssignment = secondAssignment;
    }
  }

  private static class Or extends BinaryOperator {
    Or(AttributeChooser assignment, AttributeChooser secondAssignment) {
      super(assignment, secondAssignment);
    }

    @Override
    public boolean accepts(String name, Class<?> type, AnnotationProvider annotationProvider) {
      return assignment.accepts(name, type, annotationProvider) ||
              secondAssignment.accepts(name, type, annotationProvider);
    }
  }

  private static class And extends BinaryOperator {
    And(AttributeChooser assignment, AttributeChooser secondAssignment) {
      super(assignment, secondAssignment);
    }

    @Override
    public boolean accepts(String name, Class<?> type, AnnotationProvider annotationProvider) {
      return assignment.accepts(name, type, annotationProvider) &&
              secondAssignment.accepts(name, type, annotationProvider);
    }
  }

  @Override
  public AttributeChooser or(AttributeChooser alternative) {
    return new Or(alternative, this);
  }

  @Override
  public AttributeChooser and(AttributeChooser alternative) {
    return new And(alternative, this);
  }

  @Override
  public AttributeChooser complement() {
    return new Complement(this);
  }
}
