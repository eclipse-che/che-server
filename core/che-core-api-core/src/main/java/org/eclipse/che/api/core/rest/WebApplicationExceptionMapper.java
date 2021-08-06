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

import static org.eclipse.che.dto.server.DtoFactory.newDto;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAcceptableException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import javax.inject.Singleton;
import org.eclipse.che.api.core.rest.shared.dto.ServiceError;
import org.eclipse.che.dto.server.DtoFactory;

/**
 * Mapper for the {@link WebApplicationException} exceptions.
 *
 * @author Max Shaposhnyk
 */
@Provider
@Singleton
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {

  @Override
  public Response toResponse(WebApplicationException exception) {

    ServiceError error = newDto(ServiceError.class).withMessage(exception.getMessage());

    if (exception instanceof BadRequestException) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(DtoFactory.getInstance().toJson(error))
          .type(MediaType.APPLICATION_JSON)
          .build();
    } else if (exception instanceof ForbiddenException) {
      return Response.status(Response.Status.FORBIDDEN)
          .entity(DtoFactory.getInstance().toJson(error))
          .type(MediaType.APPLICATION_JSON)
          .build();
    } else if (exception instanceof NotFoundException) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(DtoFactory.getInstance().toJson(error))
          .type(MediaType.APPLICATION_JSON)
          .build();
    } else if (exception instanceof NotAuthorizedException) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(DtoFactory.getInstance().toJson(error))
          .type(MediaType.APPLICATION_JSON)
          .build();
    } else if (exception instanceof NotAcceptableException) {
      return Response.status(Status.NOT_ACCEPTABLE)
          .entity(DtoFactory.getInstance().toJson(error))
          .type(MediaType.APPLICATION_JSON)
          .build();
    } else if (exception instanceof NotAllowedException) {
      return Response.status(Status.METHOD_NOT_ALLOWED)
          .entity(DtoFactory.getInstance().toJson(error))
          .type(MediaType.APPLICATION_JSON)
          .build();
    } else if (exception instanceof NotSupportedException) {
      return Response.status(Status.UNSUPPORTED_MEDIA_TYPE)
          .entity(DtoFactory.getInstance().toJson(error))
          .type(MediaType.APPLICATION_JSON)
          .build();
    } else {
      return Response.serverError()
          .entity(DtoFactory.getInstance().toJson(error))
          .type(MediaType.APPLICATION_JSON)
          .build();
    }
  }
}
