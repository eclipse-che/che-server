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
package org.eclipse.che.workspace.infrastructure.openshift.authorization;

import static org.eclipse.che.commons.lang.StringUtils.strToSet;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.Group;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.authorization.AuthorizationChecker;

/** This {@link OpenShiftAuthorizationCheckerImpl} checks if user is allowed to use Che. */
@Singleton
public class OpenShiftAuthorizationCheckerImpl implements AuthorizationChecker {

  private final CheServerKubernetesClientFactory cheServerKubernetesClientFactory;

  private final Set<String> allowUsers;
  private final Set<String> allowGroups;
  private final Set<String> denyUsers;
  private final Set<String> denyGroups;

  @Inject
  public OpenShiftAuthorizationCheckerImpl(
      @Nullable @Named("che.infra.kubernetes.advanced_authorization.allow_users") String allowUsers,
      @Nullable @Named("che.infra.kubernetes.advanced_authorization.allow_groups")
          String allowGroups,
      @Nullable @Named("che.infra.kubernetes.advanced_authorization.deny_users") String denyUsers,
      @Nullable @Named("che.infra.kubernetes.advanced_authorization.deny_groups") String denyGroups,
      CheServerKubernetesClientFactory cheServerKubernetesClientFactory) {
    this.allowUsers = strToSet(allowUsers);
    this.allowGroups = strToSet(allowGroups);
    this.denyUsers = strToSet(denyUsers);
    this.denyGroups = strToSet(denyGroups);
    this.cheServerKubernetesClientFactory = cheServerKubernetesClientFactory;
  }

  public boolean isAuthorized(Subject subject) throws InfrastructureException {
    String username = subject.getUserName();
    return isAllowedUser(cheServerKubernetesClientFactory.create(), username)
        && !isDeniedUser(cheServerKubernetesClientFactory.create(), username);
  }

  private boolean isAllowedUser(KubernetesClient client, String username) {
    // All users from all groups are allowed by default
    if (allowUsers.isEmpty() && allowGroups.isEmpty()) {
      return true;
    }

    if (allowUsers.contains(username)) {
      return true;
    }

    for (String groupName : allowGroups) {
      Group group = client.resources(Group.class).withName(groupName).get();
      if (group != null) {
        List<String> users = group.getUsers();
        if (users != null && users.contains(username)) {
          return true;
        }
      }
    }

    return false;
  }

  private boolean isDeniedUser(KubernetesClient client, String username) {
    // All users from all groups are allowed by default
    if (denyUsers.isEmpty() && denyGroups.isEmpty()) {
      return false;
    }

    if (denyUsers.contains(username)) {
      return true;
    }

    for (String groupName : denyGroups) {
      Group group = client.resources(Group.class).withName(groupName).get();
      if (group != null) {
        List<String> users = group.getUsers();
        if (users != null && users.contains(username)) {
          return true;
        }
      }
    }

    return false;
  }
}
