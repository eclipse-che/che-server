/*
 * Copyright (c) 2012-2021 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.commons.lang;

/** @author andrew00x */
public final class Pair<A, B> {

  public static <K, V> Pair<K, V> of(K k, V v) {
    return new Pair<>(k, v);
  }

  public final A first;
  public final B second;

  private final int hashCode;

  public Pair(A first, B second) {
    this.first = first;
    this.second = second;
    int hashCode = 7;
    hashCode = hashCode * 31 + (first == null ? 0 : first.hashCode());
    hashCode = hashCode * 31 + (second == null ? 0 : second.hashCode());
    this.hashCode = hashCode;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Pair<?, ?>)) {
      return false;
    }
    final Pair other = (Pair) o;
    return (first == null ? other.first == null : first.equals(other.first))
        && (second == null ? other.second == null : second.equals(other.second));
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public String toString() {
    return "{first=" + first + ", second=" + second + '}';
  }
}
