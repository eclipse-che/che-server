/*
 * Copyright (c) 2012-2023 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.factory.server;

public enum FactoryResolverPriority {
  DEFAULT(1),
  HIGHEST(2),
  LOWEST(0);

  private final int priority;

  FactoryResolverPriority(int priority) {
    this.priority = priority;
  }

  public int getPriority() {
    return priority;
  }
}
