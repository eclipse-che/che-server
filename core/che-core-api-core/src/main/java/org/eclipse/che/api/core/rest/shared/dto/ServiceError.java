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
package org.eclipse.che.api.core.rest.shared.dto;

import java.util.List;
import org.eclipse.che.dto.shared.DTO;

/**
 * Describes error which may be serialized to JSON format with {@link
 * org.eclipse.che.api.core.rest.ApiExceptionMapper}
 *
 * @author <a href="mailto:andrew00x@gmail.com">Andrey Parfonov</a>
 * @see org.eclipse.che.api.core.ApiException
 * @see org.eclipse.che.api.core.rest.ApiExceptionMapper
 */
@DTO
public interface ServiceError {
  /**
   * Get error message.
   *
   * @return error message
   */
  String getMessage();

  List<String> getTrace();

  ServiceError withMessage(String message);

  ServiceError withTrace(List<String> trace);

  /**
   * Set error message.
   *
   * @param message error message
   */
  void setMessage(String message);
}
