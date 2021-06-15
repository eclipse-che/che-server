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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.Set;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.rest.Service;

@Api(value = "/scm")
@Path("/scm")
public class ScmService extends Service {

  private final Set<ScmFileResolver> specificScmFileResolvers;

  @Inject
  public ScmService(Set<ScmFileResolver> specificScmFileResolvers) {
    this.specificScmFileResolvers = specificScmFileResolvers;
  }

  @GET
  @Path("/resolve")
  @ApiOperation(value = "Get file content by specific repository and filename.")
  @ApiResponses({
    @ApiResponse(code = 200, message = "Factory successfully built from parameters"),
    @ApiResponse(code = 400, message = "Missed required parameters."),
    @ApiResponse(code = 404, message = "Requested file not found."),
    @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response resolveFile(
      @ApiParam(value = "Repository URL") @QueryParam("repository") String repository,
      @ApiParam(value = "File name or path") @QueryParam("file") String filePath)
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
