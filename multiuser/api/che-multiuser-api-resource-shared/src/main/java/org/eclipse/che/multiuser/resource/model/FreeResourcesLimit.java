/*
 * Copyright (c) 2012-2025 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.multiuser.resource.model;

import java.util.List;

/**
 * Represents limit of resources which are available for free usage by some account.
 *
 * @author Sergii Leschenko
 */
public interface FreeResourcesLimit {
  /** Returns id of account that can use free resources. */
  String getAccountId();

  /** Returns resources which are available for free usage. */
  List<? extends Resource> getResources();
}
