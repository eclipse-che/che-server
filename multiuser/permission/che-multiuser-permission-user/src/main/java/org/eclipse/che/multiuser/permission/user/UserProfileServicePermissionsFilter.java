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
package org.eclipse.che.multiuser.permission.user;

import jakarta.ws.rs.Path;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.everrest.CheMethodInvokerFilter;
import org.eclipse.che.multiuser.api.permission.server.SystemDomain;
import org.everrest.core.Filter;
import org.everrest.core.resource.GenericResourceMethod;

/**
 * Filter that covers calls to {@link UserProfileServicePermissionsFilter} with authorization
 *
 * @author Sergii Leschenko
 */
@Filter
@Path("/profile{path:.*}")
public class UserProfileServicePermissionsFilter extends CheMethodInvokerFilter {
  @Override
  protected void filter(GenericResourceMethod GenericResourceMethod, Object[] arguments)
      throws ApiException {
    final String methodName = GenericResourceMethod.getMethod().getName();
    final Subject subject = EnvironmentContext.getCurrent().getSubject();
    switch (methodName) {
      case "updateAttributesById":
        subject.checkPermission(
            SystemDomain.DOMAIN_ID, null, UserServicePermissionsFilter.MANAGE_USERS_ACTION);
        break;
      default:
        // public methods
    }
  }
}
