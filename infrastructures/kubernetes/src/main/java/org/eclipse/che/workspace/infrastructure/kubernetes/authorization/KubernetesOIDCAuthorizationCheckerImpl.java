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
package org.eclipse.che.workspace.infrastructure.kubernetes.authorization;

import static org.eclipse.che.commons.lang.StringUtils.strToSet;

import java.util.Collections;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.subject.Subject;

/** This {@link KubernetesOIDCAuthorizationCheckerImpl} checks if user is allowed to use Che. */
@Singleton
public class KubernetesOIDCAuthorizationCheckerImpl implements AuthorizationChecker {

  private final Set<String> allowUsers;
  private final Set<String> allowGroups;
  private final Set<String> denyUsers;
  private final Set<String> denyGroups;

  @Inject
  public KubernetesOIDCAuthorizationCheckerImpl(
      @Nullable @Named("che.infra.kubernetes.advanced_authorization.allow_users") String allowUsers,
      @Nullable @Named("che.infra.kubernetes.advanced_authorization.allow_groups")
          String allowGroups,
      @Nullable @Named("che.infra.kubernetes.advanced_authorization.deny_users") String denyUsers,
      @Nullable @Named("che.infra.kubernetes.advanced_authorization.deny_groups")
          String denyGroups) {
    this.allowUsers = strToSet(allowUsers);
    this.allowGroups = strToSet(allowGroups);
    this.denyUsers = strToSet(denyUsers);
    this.denyGroups = strToSet(denyGroups);
  }

  public boolean isAuthorized(Subject subject) {
    return isAllowedUser(subject) && !isDeniedUser(subject);
  }

  private boolean isAllowedUser(Subject subject) {
    // All users from all groups are allowed by default
    if (allowUsers.isEmpty() && allowGroups.isEmpty()) {
      return true;
    }

    if (allowUsers.contains(subject.getUserName())) {
      return true;
    }

    return !Collections.disjoint(allowGroups, subject.getGroups());
  }

  private boolean isDeniedUser(Subject subject) {
    // All users from all groups are allowed by default
    if (denyUsers.isEmpty() && denyGroups.isEmpty()) {
      return false;
    }

    if (denyUsers.contains(subject.getUserName())) {
      return true;
    }

    return !Collections.disjoint(denyGroups, subject.getGroups());
  }
}
