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
package org.eclipse.che.multiuser.api.permission.server.filter;

import static com.google.common.base.Strings.isNullOrEmpty;

import jakarta.ws.rs.Path;
import javax.inject.Inject;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.everrest.CheMethodInvokerFilter;
import org.eclipse.che.multiuser.api.permission.server.InstanceParameterValidator;
import org.eclipse.che.multiuser.api.permission.server.SuperPrivilegesChecker;
import org.eclipse.che.multiuser.api.permission.server.filter.check.DomainsPermissionsCheckers;
import org.eclipse.che.multiuser.api.permission.shared.dto.PermissionsDto;
import org.everrest.core.Filter;
import org.everrest.core.resource.GenericResourceMethod;

/**
 * Restricts access to setting permissions of instance by users' setPermissions permission
 *
 * @author Sergii Leschenko
 */
@Filter
@Path("/permissions/")
public class SetPermissionsFilter extends CheMethodInvokerFilter {
  @Inject private SuperPrivilegesChecker superPrivilegesChecker;

  @Inject private InstanceParameterValidator instanceValidator;

  @Inject private DomainsPermissionsCheckers domainsPermissionsChecker;

  @Override
  public void filter(GenericResourceMethod genericResourceMethod, Object[] args)
      throws BadRequestException, ForbiddenException, NotFoundException, ServerException {
    if (genericResourceMethod.getMethod().getName().equals("storePermissions")) {
      final PermissionsDto permissions = (PermissionsDto) args[0];
      checkArgument(permissions != null, "Permissions descriptor required");
      final String domain = permissions.getDomainId();
      checkArgument(!isNullOrEmpty(domain), "Domain required");
      instanceValidator.validate(domain, permissions.getInstanceId());
      if (superPrivilegesChecker.isPrivilegedToManagePermissions(permissions.getDomainId())) {
        return;
      }
      domainsPermissionsChecker.getSetChecker(domain).check(permissions);
    }
  }

  private void checkArgument(boolean expression, String message) throws BadRequestException {
    if (!expression) {
      throw new BadRequestException(message);
    }
  }
}
