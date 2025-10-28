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
package org.eclipse.che.api.workspace.shared.dto.devfile;

import org.eclipse.che.api.core.model.workspace.devfile.Env;
import org.eclipse.che.dto.shared.DTO;

/**
 * @author Sergii Leshchenko
 */
@DTO
public interface EnvDto extends Env {

  @Override
  String getName();

  void setName(String name);

  EnvDto withName(String name);

  @Override
  String getValue();

  void setValue(String value);

  EnvDto withValue(String value);
}
