package org.fiolino.common.processing;

/**
 * Created by kuli on 21.12.15.
 */
public class DataIntegrityViolationException extends Exception {
  private Object id;

  public DataIntegrityViolationException() {
  }

  public DataIntegrityViolationException(String message) {
    super(message);
  }

  public DataIntegrityViolationException(String message, Throwable cause) {
    super(message, cause);
  }

  public DataIntegrityViolationException(Object id, String message, Throwable cause) {
    super(message, cause);
    setId(id);
  }

  public DataIntegrityViolationException(Throwable cause) {
    super(cause);
  }

  public DataIntegrityViolationException(Object id, String message) {
    super(message);
    setId(id);
  }

  public void setId(Object id) {
    this.id = id;
  }

  public Object getId() {
    return id;
  }

  @Override
  public String getMessage() {
    String m = super.getMessage();
    if (id != null) {
      return m + " (id=" + id + ")";
    }
    return m;
  }
}
