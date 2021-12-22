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
package org.eclipse.che.multiuser.permission.resource.filters;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import jakarta.ws.rs.Path;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import org.eclipse.che.account.api.AccountManager;
import org.eclipse.che.account.shared.model.Account;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.everrest.CheMethodInvokerFilter;
import org.eclipse.che.multiuser.api.permission.server.SystemDomain;
import org.eclipse.che.multiuser.api.permission.server.account.AccountOperation;
import org.eclipse.che.multiuser.api.permission.server.account.AccountPermissionsChecker;
import org.eclipse.che.multiuser.resource.api.usage.ResourceService;
import org.everrest.core.Filter;
import org.everrest.core.resource.GenericResourceMethod;

/**
 * Restricts access to methods of {@link ResourceService} by users' permissions.
 *
 * <p>Filter contains rules for protecting of all methods of {@link ResourceService}.<br>
 * In case when requested method is unknown filter throws {@link ForbiddenException}
 *
 * @author Sergii Leschenko
 */
@Filter
@Path("/resource{path:(?!/free)(/.*)?}")
public class ResourceServicePermissionsFilter extends CheMethodInvokerFilter {
  static final String GET_TOTAL_RESOURCES_METHOD = "getTotalResources";
  static final String GET_AVAILABLE_RESOURCES_METHOD = "getAvailableResources";
  static final String GET_USED_RESOURCES_METHOD = "getUsedResources";
  static final String GET_RESOURCES_DETAILS_METHOD = "getResourceDetails";

  private final AccountManager accountManager;
  private final Map<String, AccountPermissionsChecker> permissionsCheckers;

  @Inject
  public ResourceServicePermissionsFilter(
      AccountManager accountManager, Set<AccountPermissionsChecker> permissionsCheckers) {
    this.accountManager = accountManager;
    this.permissionsCheckers =
        permissionsCheckers.stream()
            .collect(toMap(AccountPermissionsChecker::getAccountType, identity()));
  }

  @Override
  protected void filter(GenericResourceMethod genericMethodResource, Object[] arguments)
      throws ApiException {
    String accountId;
    switch (genericMethodResource.getMethod().getName()) {
      case GET_TOTAL_RESOURCES_METHOD:
      case GET_AVAILABLE_RESOURCES_METHOD:
      case GET_USED_RESOURCES_METHOD:
      case GET_RESOURCES_DETAILS_METHOD:
        Subject currentSubject = EnvironmentContext.getCurrent().getSubject();
        if (currentSubject.hasPermission(
            SystemDomain.DOMAIN_ID, null, SystemDomain.MANAGE_SYSTEM_ACTION)) {
          // user is admin and he is able to see resources of all accounts
          return;
        }
        accountId = ((String) arguments[0]);
        break;
      default:
        throw new ForbiddenException("The user does not have permission to perform this operation");
    }
    final Account account = accountManager.getById(accountId);

    final AccountPermissionsChecker resourcesPermissionsChecker =
        permissionsCheckers.get(account.getType());
    if (resourcesPermissionsChecker != null) {
      resourcesPermissionsChecker.checkPermissions(
          accountId, AccountOperation.SEE_RESOURCE_INFORMATION);
    } else {
      throw new ForbiddenException("User is not authorized to perform given operation");
    }
  }
}
