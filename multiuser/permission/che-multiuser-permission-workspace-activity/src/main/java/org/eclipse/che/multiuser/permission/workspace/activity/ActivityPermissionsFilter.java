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
package org.eclipse.che.multiuser.permission.workspace.activity;

import jakarta.ws.rs.Path;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.everrest.CheMethodInvokerFilter;
import org.eclipse.che.multiuser.api.permission.server.SystemDomain;
import org.eclipse.che.multiuser.permission.workspace.server.WorkspaceDomain;
import org.everrest.core.Filter;
import org.everrest.core.resource.GenericResourceMethod;

/**
 * Restricts access to methods of {@link WorkspaceActivityService} by user's permissions
 *
 * @author Max Shaposhnik (mshaposhnik@codenvy.com)
 */
@Filter
@Path("/activity{path:(/.*)?}")
public class ActivityPermissionsFilter extends CheMethodInvokerFilter {

  @Override
  protected void filter(GenericResourceMethod genericResourceMethod, Object[] arguments)
      throws ApiException {
    final String methodName = genericResourceMethod.getMethod().getName();

    final Subject currentSubject = EnvironmentContext.getCurrent().getSubject();
    String domain;
    String action;
    String instance;

    switch (methodName) {
      case "active":
        domain = WorkspaceDomain.DOMAIN_ID;
        instance = (String) arguments[0];
        action = WorkspaceDomain.USE;
        break;
      case "getWorkspacesByActivity":
        domain = SystemDomain.DOMAIN_ID;
        instance = null;
        action = SystemDomain.MONITOR_SYSTEM_ACTION;
        break;
      default:
        throw new ForbiddenException("The user does not have permission to perform this operation");
    }
    currentSubject.checkPermission(domain, instance, action);
  }
}
