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
import java.util.Set;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.rest.Service;

@Api(value = "/scm")
@Path("/scm")
public class ScmFileResolverService extends Service {

  @GET
  @Path("/resolve")
  public Response resolveFile(
      @QueryParam("repository") String repository, @QueryParam("file") String filePath)
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

  /** Optional inject for the resolvers. */
  @com.google.inject.Inject(optional = true)
  @SuppressWarnings("unused")
  private Set<ScmFileResolver> specificScmFileResolvers;

  /**
   * Provides a suitable file resolver for the given parameters. If there is no at least one
   * resolver able to process parameters,then {@link BadRequestException} will be thrown
   *
   * @return suitable service-specific resolver or default one
   */
  public ScmFileResolver getScmFileResolver(String repository) throws BadRequestException {
    if (specificScmFileResolvers != null) {
      for (ScmFileResolver scmFileResolver : specificScmFileResolvers) {
        if (scmFileResolver.accept(repository)) {
          return scmFileResolver;
        }
      }
    }
    throw new BadRequestException("Cannot find suitable file resolver for the provided URL.");
  }
}
