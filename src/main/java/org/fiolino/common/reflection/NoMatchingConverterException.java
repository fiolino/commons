package org.fiolino.common.reflection;

/**
 * Created by Kuli on 6/20/2016.
 */
public class NoMatchingConverterException extends RuntimeException {
  private static final long serialVersionUID = -6050857567215258754L;

  public NoMatchingConverterException() {
  }

  public NoMatchingConverterException(String message) {
    super(message);
  }

  public NoMatchingConverterException(String message, Throwable cause) {
    super(message, cause);
  }

  public NoMatchingConverterException(Throwable cause) {
    super(cause);
  }
}
