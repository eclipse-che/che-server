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
package org.eclipse.che.api.workspace.shared.dto;

import org.eclipse.che.api.core.model.project.ProjectProblem;
import org.eclipse.che.dto.shared.DTO;

/**
 * @author Sergii Kabashniuk
 */
@DTO
public interface ProjectProblemDto extends ProjectProblem {

  int getCode();

  void setCode(int status);

  ProjectProblemDto withCode(int status);

  String getMessage();

  void setMessage(String message);

  ProjectProblemDto withMessage(String message);
}
