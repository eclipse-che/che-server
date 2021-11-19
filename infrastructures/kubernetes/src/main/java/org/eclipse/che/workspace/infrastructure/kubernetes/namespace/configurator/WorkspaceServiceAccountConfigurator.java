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
package org.eclipse.che.workspace.infrastructure.kubernetes.namespace.configurator;

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesWorkspaceServiceAccount;

/**
 * This {@link NamespaceConfigurator} ensures that workspace ServiceAccount with proper ClusterRole
 * is set in Workspace namespace.
 */
@Singleton
public class WorkspaceServiceAccountConfigurator implements NamespaceConfigurator {

  private final KubernetesClientFactory clientFactory;

  private final String serviceAccountName;
  private final Set<String> clusterRoleNames;

  @Inject
  public WorkspaceServiceAccountConfigurator(
      @Nullable @Named("che.infra.kubernetes.service_account_name") String serviceAccountName,
      @Nullable @Named("che.infra.kubernetes.workspace_sa_cluster_roles") String clusterRoleNames,
      KubernetesClientFactory clientFactory) {
    this.clientFactory = clientFactory;
    this.serviceAccountName = serviceAccountName;
    if (!isNullOrEmpty(clusterRoleNames)) {
      this.clusterRoleNames =
          Sets.newHashSet(
              Splitter.on(",").trimResults().omitEmptyStrings().split(clusterRoleNames));
    } else {
      this.clusterRoleNames = Collections.emptySet();
    }
  }

  @Override
  public void configure(NamespaceResolutionContext namespaceResolutionContext, String namespaceName)
      throws InfrastructureException {
    if (!isNullOrEmpty(serviceAccountName)) {
      KubernetesWorkspaceServiceAccount workspaceServiceAccount =
          doCreateServiceAccount(namespaceResolutionContext.getWorkspaceId(), namespaceName);
      workspaceServiceAccount.prepare();
    }
  }

  @VisibleForTesting
  public KubernetesWorkspaceServiceAccount doCreateServiceAccount(
      String workspaceId, String namespaceName) {
    return new KubernetesWorkspaceServiceAccount(
        workspaceId, namespaceName, serviceAccountName, clusterRoleNames, clientFactory);
  }
}
