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
package org.eclipse.che.swagger.rest;

import io.swagger.jaxrs.listing.ApiListingResource;
import jakarta.servlet.ServletConfig;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@Path("/docs/swagger.{type:json|yaml}")
public class SwaggerSpecificationService extends ApiListingResource {

  @Override
  public Response getListing(
      @Context Application app,
      @Context ServletConfig sc,
      @Context HttpHeaders headers,
      @Context UriInfo uriInfo,
      @PathParam("type") String type) {
    if (type.trim().equalsIgnoreCase("yaml")) {
      return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }
    return super.getListing(app, sc, headers, uriInfo, type);
  }
}
