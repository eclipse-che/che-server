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
package org.eclipse.che.api.core.rest;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import javax.inject.Singleton;
import org.eclipse.che.api.core.AuthenticationException;
import org.eclipse.che.dto.server.DtoFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * jakarta.ws.rs.ext.ExceptionMapper for AuthenticationException
 *
 * @author Alexander Garagatyi
 */
@Provider
@Singleton
public class AuthenticationExceptionMapper implements ExceptionMapper<AuthenticationException> {
  private static final Logger LOG = LoggerFactory.getLogger(AuthenticationExceptionMapper.class);

  @Override
  public Response toResponse(AuthenticationException exception) {
    LOG.debug(exception.getLocalizedMessage());

    int responseStatus = exception.getResponseStatus();
    String message = exception.getMessage();
    if (message != null) {
      return Response.status(responseStatus)
          .entity(DtoFactory.getInstance().toJson(exception.getServiceError()))
          .type(MediaType.APPLICATION_JSON)
          .build();
    }
    return Response.status(responseStatus).build();
  }
}
