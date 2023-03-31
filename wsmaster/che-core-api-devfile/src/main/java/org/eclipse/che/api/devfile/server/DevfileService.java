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
package org.eclipse.che.api.devfile.server;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.eclipse.che.api.workspace.server.devfile.Constants.CURRENT_API_VERSION;
import static org.eclipse.che.api.workspace.server.devfile.Constants.SUPPORTED_VERSIONS;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.io.FileNotFoundException;
import java.io.IOException;
import javax.inject.Inject;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.api.workspace.server.devfile.schema.DevfileSchemaProvider;

/** Defines Devfile REST API. */
@Deprecated
@Tag(name = "devfile", description = "Devfile REST API")
@Path("/devfile")
public class DevfileService extends Service {

  private final DevfileSchemaProvider schemaCachedProvider;

  @Inject
  public DevfileService(DevfileSchemaProvider schemaCachedProvider) {
    this.schemaCachedProvider = schemaCachedProvider;
  }

  /**
   * Retrieves the json schema.
   *
   * @return json schema
   */
  @GET
  @Produces(APPLICATION_JSON)
  @Operation(
      summary = "Retrieves current version of devfile JSON schema",
      responses = {
        @ApiResponse(responseCode = "200", description = "The schema successfully retrieved"),
        @ApiResponse(
            responseCode = "404",
            description = "The schema for given version was not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error occurred")
      })
  public Response getSchema(
      @Parameter(description = "Devfile schema version")
          @DefaultValue(CURRENT_API_VERSION)
          @QueryParam("version")
          String version)
      throws ServerException, NotFoundException {
    if (!SUPPORTED_VERSIONS.contains(version)) {
      throw new NotFoundException(
          String.format(
              "Devfile schema version '%s' is invalid or not supported. Supported versions are '%s'.",
              version, SUPPORTED_VERSIONS));
    }

    try {
      return Response.ok(schemaCachedProvider.getSchemaContent(version)).build();
    } catch (FileNotFoundException e) {
      throw new NotFoundException(e.getLocalizedMessage());
    } catch (IOException e) {
      throw new ServerException(e);
    }
  }
}
