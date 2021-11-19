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

import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Collections;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;

/**
 * This {@link NamespaceConfigurator} ensures that User has assigned configured ClusterRoles from
 * `che.infra.kubernetes.user_cluster_roles` property. These are assigned with RoleBindings in
 * user's namespace.
 */
@Singleton
public class UserPermissionConfigurator implements NamespaceConfigurator {

  private final Set<String> userClusterRoles;
  private final KubernetesClientFactory clientFactory;

  @Inject
  public UserPermissionConfigurator(
      @Nullable @Named("che.infra.kubernetes.user_cluster_roles") String userClusterRoles,
      CheServerKubernetesClientFactory cheClientFactory) {
    this.clientFactory = cheClientFactory;
    if (!isNullOrEmpty(userClusterRoles)) {
      this.userClusterRoles =
          Sets.newHashSet(
              Splitter.on(",").trimResults().omitEmptyStrings().split(userClusterRoles));
    } else {
      this.userClusterRoles = Collections.emptySet();
    }
  }

  @Override
  public void configure(NamespaceResolutionContext namespaceResolutionContext, String namespaceName)
      throws InfrastructureException {
    if (!userClusterRoles.isEmpty()) {
      bindRoles(
          clientFactory.create(),
          namespaceName,
          namespaceResolutionContext.getUserName(),
          userClusterRoles);
    }
  }

  private void bindRoles(
      KubernetesClient client, String namespaceName, String username, Set<String> clusterRoles) {
    for (String clusterRole : clusterRoles) {
      client
          .rbac()
          .roleBindings()
          .inNamespace(namespaceName)
          .createOrReplace(
              new RoleBindingBuilder()
                  .withNewMetadata()
                  .withName(clusterRole)
                  .endMetadata()
                  .addToSubjects(
                      new io.fabric8.kubernetes.api.model.rbac.Subject(
                          "rbac.authorization.k8s.io", "User", username, namespaceName))
                  .withNewRoleRef()
                  .withApiGroup("rbac.authorization.k8s.io")
                  .withKind("ClusterRole")
                  .withName(clusterRole)
                  .endRoleRef()
                  .build());
    }
  }
}
