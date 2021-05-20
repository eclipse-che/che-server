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
package org.eclipse.che.api.core.rest.shared.dto;

import java.util.List;
import org.eclipse.che.api.core.rest.shared.ParameterType;
import org.eclipse.che.dto.shared.DTO;

/**
 * Describes one query parameter of the request.
 *
 * @author <a href="mailto:andrew00x@gmail.com">Andrey Parfonov</a>
 * @see org.eclipse.che.api.core.rest.annotations.Description
 * @see org.eclipse.che.api.core.rest.annotations.Required
 * @see org.eclipse.che.api.core.rest.annotations.Valid
 */
@DTO
public interface LinkParameter {
  /**
   * Get name of parameter.
   *
   * @return name of parameter
   */
  String getName();

  LinkParameter withName(String name);

  /**
   * Set name of parameter.
   *
   * @param name name of parameter
   */
  void setName(String name);

  /**
   * Get defaultValue of parameter.
   *
   * @return defaultValue of parameter
   */
  String getDefaultValue();

  LinkParameter withDefaultValue(String defaultValue);

  /**
   * Set defaultValue of parameter.
   *
   * @param defaultValue defaultValue of parameter
   */
  void setDefaultValue(String defaultValue);

  /**
   * Get optional description of parameter.
   *
   * @return optional description of parameter
   * @see org.eclipse.che.api.core.rest.annotations.Description
   */
  String getDescription();

  LinkParameter withDescription(String description);

  /**
   * Set optional description of parameter.
   *
   * @param description optional description of parameter
   * @see org.eclipse.che.api.core.rest.annotations.Description
   */
  void setDescription(String description);

  /**
   * Get type of parameter.
   *
   * @return type of parameter
   * @see org.eclipse.che.api.core.rest.shared.ParameterType
   */
  ParameterType getType();

  LinkParameter withType(ParameterType type);

  /**
   * Set type of parameter.
   *
   * @param type type of parameter
   * @see ParameterType
   */
  void setType(ParameterType type);

  /**
   * Reports whether the parameter is mandatory.
   *
   * @return {@code true} if parameter is required and {@code false} otherwise
   * @see org.eclipse.che.api.core.rest.annotations.Required
   */
  boolean isRequired();

  LinkParameter withRequired(boolean required);

  /**
   * @param required {@code true} if parameter is required and {@code false} otherwise
   * @see org.eclipse.che.api.core.rest.annotations.Required
   */
  void setRequired(boolean required);

  /**
   * Get optional list of constraint strings.
   *
   * @return optional list of constraint strings
   * @see org.eclipse.che.api.core.rest.annotations.Valid
   */
  List<String> getValid();

  LinkParameter withValid(List<String> valid);

  /**
   * Set optional list of constraint strings.
   *
   * @param valid optional list of constraint strings
   * @see org.eclipse.che.api.core.rest.annotations.Valid
   */
  void setValid(List<String> valid);
}
