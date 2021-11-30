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
package org.eclipse.che.api.infraproxy.server;

import com.google.common.annotations.Beta;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.RuntimeInfrastructure;
import org.eclipse.che.commons.annotation.Nullable;

/**
 * We use this to give our clients the direct access to the underlying infrastructure REST API. This
 * is only allowed when we can properly impersonate the user - e.g. on OpenShift with OpenShift
 * OAuth switched on.
 */
@Hidden
@Beta
@Path(InfrastructureApiService.PATH_PREFIX)
public class InfrastructureApiService extends Service {
  static final String PATH_PREFIX = "/unsupported/k8s";
  private static final int PATH_PREFIX_LENGTH = PATH_PREFIX.length();

  private final boolean allowed;
  private final RuntimeInfrastructure runtimeInfrastructure;

  private static boolean determineAllowed(String identityProvider) {
    return identityProvider != null;
  }

  @Inject
  public InfrastructureApiService(
      @Nullable @Named("che.infra.openshift.oauth_identity_provider") String identityProvider,
      RuntimeInfrastructure runtimeInfrastructure) {
    this.runtimeInfrastructure = runtimeInfrastructure;
    this.allowed = determineAllowed(identityProvider);
  }

  @GET
  @Path("{path:.+}")
  public Response get(@Context HttpHeaders headers)
      throws InfrastructureException, ApiException, IOException {
    return request("GET", headers, null);
  }

  @HEAD
  @Path("{path:.+}")
  public Response head(@Context HttpHeaders headers)
      throws InfrastructureException, ApiException, IOException {
    return request("HEAD", headers, null);
  }

  @POST
  @Path("{path:.+}")
  public Response post(@Context HttpHeaders headers, InputStream body)
      throws InfrastructureException, IOException, ApiException {
    return request("POST", headers, body);
  }

  @DELETE
  @Path("{path:.+}")
  public Response delete(@Context HttpHeaders headers, InputStream body)
      throws InfrastructureException, IOException, ApiException {
    return request("DELETE", headers, body);
  }

  @PUT
  @Path("{path:.+}")
  public Response put(@Context HttpHeaders headers, InputStream body)
      throws InfrastructureException, IOException, ApiException {
    return request("PUT", headers, body);
  }

  @OPTIONS
  @Path("{path:.+}")
  public Response options(@Context HttpHeaders headers)
      throws InfrastructureException, ApiException, IOException {
    return request("OPTIONS", headers, null);
  }

  @PATCH
  @Path("{path:.+}")
  public Response patch(@Context HttpHeaders headers, InputStream body)
      throws InfrastructureException, IOException, ApiException {
    return request("PATCH", headers, body);
  }

  private void auth() throws ApiException {
    if (!allowed) {
      throw new ForbiddenException(
          "Interaction with backing infrastructure is only allowed in multi-user mode with OpenShift OAuth");
    }
  }

  private Response request(String method, HttpHeaders headers, @Nullable InputStream body)
      throws ApiException, IOException, InfrastructureException {
    auth();
    return runtimeInfrastructure.sendDirectInfrastructureRequest(
        method, relativizeRequestAndStripPrefix(), headers, body);
  }

  /**
   * We need to strip our prefix from the request path before sending it to the infrastructure. The
   * infrastructure is unaware of where we deployed our proxy.
   *
   * @return the relative URI composed from the current request
   */
  private URI relativizeRequestAndStripPrefix() {
    URI unstrippedRelative = uriInfo.getBaseUri().relativize(uriInfo.getRequestUri());
    String str = unstrippedRelative.toString();
    return URI.create(str.substring(PATH_PREFIX_LENGTH));
  }
}
