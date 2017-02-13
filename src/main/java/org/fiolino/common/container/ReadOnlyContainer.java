package org.fiolino.common.container;

/**
 * @author kuli
 */
public interface ReadOnlyContainer {

  /**
   * Gets the value for a specific type, if available.
   *
   * @return The element as an Optional
   */
  <E> E get(Selector<E> selector);
}
