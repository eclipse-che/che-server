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
package org.eclipse.che.api.core;

import org.eclipse.che.api.core.rest.shared.dto.ServiceError;

/**
 * A {@code ServerException} is thrown as a result of an error that internal server error.
 *
 * <p>Typically in REST API such errors are converted in HTTP response with status 500.
 *
 * @author andrew00x
 */
@SuppressWarnings("serial")
public class ServerException extends ApiException {

  public ServerException(String message) {
    super(message);
  }

  public ServerException(ServiceError serviceError) {
    super(serviceError);
  }

  public ServerException(Throwable cause) {
    super(cause);
  }

  public ServerException(String message, Throwable cause) {
    super(message, cause);
  }
}
