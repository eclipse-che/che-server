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

import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.commons.annotation.Nullable;

/** This {@link KubernetesAuthorizationCheckerImpl} checks if user is allowed to use Che. */
@Singleton
public class KubernetesAuthorizationCheckerImpl implements AuthorizationChecker {

  private final Set<String> allowedUsers;
  private final Set<String> disabledUsers;

  @Inject
  public KubernetesAuthorizationCheckerImpl(
      @Nullable @Named("che.infra.kubernetes.advanced_authorization.allowed_users")
          String allowedUsers,
      @Nullable @Named("che.infra.kubernetes.advanced_authorization.disabled_users")
          String disabledUsers) {
    this.allowedUsers = strToSet(allowedUsers);
    this.disabledUsers = strToSet(disabledUsers);
  }

  public boolean isAuthorized(String username) {
    return isAllowedUser(username) && !isDisabledUser(username);
  }

  private boolean isAllowedUser(String username) {
    return allowedUsers.isEmpty() || allowedUsers.contains(username);
  }

  private boolean isDisabledUser(String username) {
    return !disabledUsers.isEmpty() && disabledUsers.contains(username);
  }
}
