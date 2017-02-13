package org.fiolino.common.reflection;

import org.fiolino.common.reflection.otherpackage.ClassFromOtherPackage;

/**
 * Created by Kuli on 6/16/2016.
 */
public class OverwritingFromOtherPackage extends ClassFromOtherPackage {
  public void publicMethod2() {}
  void packageMethod2() {}
  protected void protectedMethod2() {}
  private void privateMethod2() {}

  // This is overwritten:

  @Override
  public void publicMethod() {
    super.publicMethod();
  }

  @Override
  protected void protectedMethod() {
    super.protectedMethod();
  }
}
