package org.fiolino.common.reflection;

/**
 * Created by Kuli on 7/29/2016.
 */
public class TooManyArgumentsExpectedException extends IllegalArgumentException {

  private static final long serialVersionUID = 6011795841043005055L;

  public TooManyArgumentsExpectedException() {
  }

  public TooManyArgumentsExpectedException(String s) {
    super(s);
  }

  public TooManyArgumentsExpectedException(String message, Throwable cause) {
    super(message, cause);
  }

  public TooManyArgumentsExpectedException(Throwable cause) {
    super(cause);
  }
}
