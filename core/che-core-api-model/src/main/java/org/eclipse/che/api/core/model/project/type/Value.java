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
package org.eclipse.che.api.core.model.project.type;

import java.util.List;

/**
 * Attribute value
 *
 * @author gazarenkov
 */
public interface Value {

  /** @return value as String. If attribute has multiple values it returns first one. */
  String getString();

  /** @return value as list of strings */
  List<String> getList();

  /** @return whether the value is not initialized */
  boolean isEmpty();
}
