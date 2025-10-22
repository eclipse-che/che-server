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
 * Permits account to use some resources.
 *
 * @author gazarenkov
 * @author Sergii Leschenko
 */
public interface ResourcesDetails {
  /** Returns id of account that is owner of these resources. */
  String getAccountId();

  /** Returns detailed list of resources which can be used by owner. */
  List<? extends ProvidedResources> getProvidedResources();

  /** Returns list of resources which can be used by owner. */
  List<? extends Resource> getTotalResources();
}
