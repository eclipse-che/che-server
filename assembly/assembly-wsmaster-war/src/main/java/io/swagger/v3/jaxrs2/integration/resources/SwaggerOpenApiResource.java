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
package io.swagger.v3.jaxrs2.integration.resources;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

// @Path("/docs/swagger.{type:json|yaml}")
@Path("/openapi.{type:json|yaml}")
public class SwaggerOpenApiResource extends OpenApiResource {

  //  @Override
  //  public Response getListing(
  //      @Context Application app,
  //      @Context ServletConfig sc,
  //      @Context HttpHeaders headers,
  //      @Context UriInfo uriInfo,
  //      @PathParam("type") String type) {
  //    if (type.trim().equalsIgnoreCase("yaml")) {
  //      return Response.status(Response.Status.NOT_IMPLEMENTED).build();
  //    }
  //    return super.getListing(app, sc, headers, uriInfo, type);
  //  }

  //  @GET
  //  @Produces({"application/json", "application/yaml"})
  //  @Operation(
  //          hidden = true
  //  )
  public Response getOpenApi(
      @Context HttpHeaders headers, @Context UriInfo uriInfo, @PathParam("type") String type)
      throws Exception {
    if (type.trim().equalsIgnoreCase("yaml")) {
      return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }
    return super.getOpenApi(headers, this.config, this.app, uriInfo, type);
  }
}
