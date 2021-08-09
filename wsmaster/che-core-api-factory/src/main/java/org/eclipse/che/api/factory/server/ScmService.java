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
package org.eclipse.che.api.factory.server;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.util.Set;
import javax.inject.Inject;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.rest.Service;

@Tag(name = "scm")
@Path("/scm")
public class ScmService extends Service {

  private final Set<ScmFileResolver> specificScmFileResolvers;

  @Inject
  public ScmService(Set<ScmFileResolver> specificScmFileResolvers) {
    this.specificScmFileResolvers = specificScmFileResolvers;
  }

  @GET
  @Path("/resolve")
  @Operation(
      summary = "Get file content by specific repository and filename.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Factory successfully built from parameters"),
        @ApiResponse(responseCode = "400", description = "Missed required parameters."),
        @ApiResponse(responseCode = "404", description = "Requested file not found."),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  public Response resolveFile(
      @Parameter(description = "Repository URL") @QueryParam("repository") String repository,
      @Parameter(description = "File name or path") @QueryParam("file") String filePath)
      throws ApiException {
    requireNonNull(repository, "Repository");
    requireNonNull(repository, "File");
    String content = getScmFileResolver(repository).fileContent(repository, filePath);
    return Response.ok().entity(content).build();
  }

  /**
   * Checks object reference is not {@code null}
   *
   * @param object object reference to check
   * @param subject used as subject of exception message "{subject} required"
   * @throws BadRequestException when object reference is {@code null}
   */
  private static void requireNonNull(Object object, String subject) throws BadRequestException {
    if (object == null) {
      throw new BadRequestException(subject + " parameter is required");
    }
  }

  /**
   * Provides a suitable file resolver for the given parameters. If there is no at least one
   * resolver able to process parameters,then {@link BadRequestException} will be thrown
   *
   * @return suitable service-specific resolver or default one
   */
  public ScmFileResolver getScmFileResolver(String repository) throws BadRequestException {
    for (ScmFileResolver scmFileResolver : specificScmFileResolvers) {
      if (scmFileResolver.accept(repository)) {
        return scmFileResolver;
      }
    }
    throw new BadRequestException("Cannot find suitable file resolver for the provided URL.");
  }
}
