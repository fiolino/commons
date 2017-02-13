package org.fiolino.common.reflection;

import org.fiolino.common.reflection.otherpackage.ClassFromOtherPackage;

import java.lang.invoke.MethodHandles;

/**
 * Created by Kuli on 6/16/2016.
 */
public class LocalClass extends ClassFromOtherPackage {
  public void publicMethod2() {}
  void packageMethod2() {}
  protected void protectedMethod2() {}
  private void privateMethod2() {}

  MethodHandles.Lookup lookup() {
    return MethodHandles.lookup();
  }
}
