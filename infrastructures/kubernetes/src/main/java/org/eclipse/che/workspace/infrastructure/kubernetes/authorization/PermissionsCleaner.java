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
package org.eclipse.che.workspace.infrastructure.kubernetes.authorization;

import static org.eclipse.che.commons.lang.StringUtils.strToSet;

import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;

/** This {@link PermissionsCleaner} cleans up all user's permissions. */
@Singleton
public class PermissionsCleaner {

  private final Set<String> userClusterRoles;
  private final CheServerKubernetesClientFactory cheServerKubernetesClientFactory;

  @Inject
  public PermissionsCleaner(
      @Nullable @Named("che.infra.kubernetes.user_cluster_roles") String userClusterRoles,
      CheServerKubernetesClientFactory cheServerKubernetesClientFactory) {
    this.cheServerKubernetesClientFactory = cheServerKubernetesClientFactory;
    this.userClusterRoles = strToSet(userClusterRoles);
  }

  public void cleanUp(String namespaceName) throws InfrastructureException {
    KubernetesClient client = cheServerKubernetesClientFactory.create();
    for (String userClusterRole : userClusterRoles) {
      client.rbac().roleBindings().inNamespace(namespaceName).withName(userClusterRole).delete();
    }
  }
}
