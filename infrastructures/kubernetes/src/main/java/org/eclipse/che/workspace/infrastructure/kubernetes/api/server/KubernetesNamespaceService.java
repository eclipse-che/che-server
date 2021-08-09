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
package org.eclipse.che.workspace.infrastructure.kubernetes.api.server;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import com.google.common.annotations.Beta;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.dto.server.DtoFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.api.shared.KubernetesNamespaceMeta;
import org.eclipse.che.workspace.infrastructure.kubernetes.api.shared.dto.KubernetesNamespaceMetaDto;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespaceFactory;

/** @author Sergii Leshchenko */
@Tag(name = "kubernetes-namespace",
    description = "Kubernetes REST API for working with Namespaces")
@Path("/kubernetes/namespace")
@Beta
public class KubernetesNamespaceService extends Service {

  private final KubernetesNamespaceFactory namespaceFactory;

  @Inject
  public KubernetesNamespaceService(KubernetesNamespaceFactory namespaceFactory) {
    this.namespaceFactory = namespaceFactory;
  }

  @GET
  @Produces(APPLICATION_JSON)
  @Operation(summary = "Get k8s namespaces where user is able to create workspaces. This operation can be performed only by authorized user."
              + "This is under beta and may be significant changed",
      response = String.class,
      responseContainer = "List",
          responses = {
    @ApiResponse(responseCode = "200", description = "The namespaces successfully fetched"),
    @ApiResponse(responseCode = "500", description = "Internal server error occurred during namespaces fetching")
  })
  public List<KubernetesNamespaceMetaDto> getNamespaces() throws InfrastructureException {
    return namespaceFactory.list().stream().map(this::asDto).collect(Collectors.toList());
  }

  @POST
  @Path("provision")
  @Produces(APPLICATION_JSON)
  @Operation(summary = "Provision k8s namespace where user is able to create workspaces. This operation can be performed only by an authorized user."
              + " This is a beta feature that may be significantly changed.",
      response = KubernetesNamespaceMetaDto.class,
          responses = {
    @ApiResponse(responseCode = "200", description = "The namespace successfully provisioned"),
    @ApiResponse(
        code = 500,
        description = "Internal server error occurred during namespace provisioning")
  })
  public KubernetesNamespaceMetaDto provision() throws InfrastructureException {
    return asDto(
        namespaceFactory.provision(
            new NamespaceResolutionContext(EnvironmentContext.getCurrent().getSubject())));
  }

  private KubernetesNamespaceMetaDto asDto(KubernetesNamespaceMeta kubernetesNamespaceMeta) {
    return DtoFactory.newDto(KubernetesNamespaceMetaDto.class)
        .withName(kubernetesNamespaceMeta.getName())
        .withAttributes(kubernetesNamespaceMeta.getAttributes());
  }
}
