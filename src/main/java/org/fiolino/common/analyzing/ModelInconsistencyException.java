package org.fiolino.common.analyzing;

/**
 * If this is thrown, something in the model classes is defined wrongly.
 *
 * Created by Michael Kuhlmann on 07.04.2016.
 */
public class ModelInconsistencyException extends RuntimeException {
  public ModelInconsistencyException() {
  }

  public ModelInconsistencyException(String message) {
    super(message);
  }

  public ModelInconsistencyException(String message, Throwable cause) {
    super(message, cause);
  }

  public ModelInconsistencyException(Throwable cause) {
    super(cause);
  }
}
