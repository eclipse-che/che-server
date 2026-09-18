/*
 * Copyright (c) 2012-2026 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.security.oauth.kubernetes;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.security.oauth.UserWorkspaceUrlProvider;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.api.shared.KubernetesNamespaceMeta;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespaceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the main URLs of the current user's workspaces from the {@code DevWorkspace} custom
 * resources living in the namespaces of that user.
 *
 * <p>{@code status.mainUrl} is published by the DevWorkspace Operator and holds the URL of the
 * endpoint marked with the {@code type: main} attribute, which for a browser IDE is the URL the
 * workbench itself is served from. Comparing against it, rather than reconstructing the URL layout
 * that the Che operator generates, keeps this check correct for both the {@code
 * /<username>/<workspace-name>/<port>/} and the legacy {@code /<workspace-id>/<component>/<port>/}
 * path strategies, as well as for subdomain based routing.
 */
@Singleton
public class KubernetesUserWorkspaceUrlProvider implements UserWorkspaceUrlProvider {
  private static final Logger LOG =
      LoggerFactory.getLogger(KubernetesUserWorkspaceUrlProvider.class);

  private static final ResourceDefinitionContext DEV_WORKSPACE_CONTEXT =
      new ResourceDefinitionContext.Builder()
          .withGroup("workspace.devfile.io")
          .withVersion("v1alpha2")
          .withKind("DevWorkspace")
          .withPlural("devworkspaces")
          .withNamespaced(true)
          .build();

  private final KubernetesNamespaceFactory namespaceFactory;
  private final CheServerKubernetesClientFactory cheServerKubernetesClientFactory;

  @Inject
  public KubernetesUserWorkspaceUrlProvider(
      KubernetesNamespaceFactory namespaceFactory,
      CheServerKubernetesClientFactory cheServerKubernetesClientFactory) {
    this.namespaceFactory = namespaceFactory;
    this.cheServerKubernetesClientFactory = cheServerKubernetesClientFactory;
  }

  @Override
  public Set<String> getWorkspaceUrls() throws ServerException {
    Set<String> urls = new LinkedHashSet<>();
    try {
      for (KubernetesNamespaceMeta namespace : namespaceFactory.list()) {
        List<GenericKubernetesResource> devWorkspaces =
            cheServerKubernetesClientFactory
                .create()
                .genericKubernetesResources(DEV_WORKSPACE_CONTEXT)
                .inNamespace(namespace.getName())
                .list()
                .getItems();
        for (GenericKubernetesResource devWorkspace : devWorkspaces) {
          Object mainUrl = devWorkspace.get("status", "mainUrl");
          if (mainUrl instanceof String && !((String) mainUrl).isBlank()) {
            urls.add((String) mainUrl);
          }
        }
      }
    } catch (InfrastructureException | KubernetesClientException e) {
      throw new ServerException(
          "Failed to read the workspaces of the current user: " + e.getMessage(), e);
    }
    LOG.debug("Resolved {} workspace URL(s) for the current user", urls.size());
    return urls;
  }
}
