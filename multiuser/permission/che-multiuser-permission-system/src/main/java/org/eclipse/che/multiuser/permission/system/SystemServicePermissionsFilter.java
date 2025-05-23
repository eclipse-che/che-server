/*
 * Copyright (c) 2012-2025 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.multiuser.permission.system;

import jakarta.ws.rs.Path;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.system.server.SystemService;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.everrest.CheMethodInvokerFilter;
import org.eclipse.che.multiuser.api.permission.server.SystemDomain;
import org.everrest.core.Filter;
import org.everrest.core.resource.GenericResourceMethod;

/**
 * Rejects/allows access to the methods of {@link SystemService}
 *
 * @author Yevhenii Voevodin
 */
@Filter
@Path("/system{path:.*}")
public class SystemServicePermissionsFilter extends CheMethodInvokerFilter {
  @Override
  protected void filter(GenericResourceMethod resource, Object[] args) throws ApiException {
    switch (resource.getMethod().getName()) {
      case "stop":
        EnvironmentContext.getCurrent()
            .getSubject()
            .checkPermission(SystemDomain.DOMAIN_ID, null, SystemDomain.MANAGE_SYSTEM_ACTION);
        break;
      case "getState":
        break;
      default:
        throw new ForbiddenException("The user does not have permission to perform this operation");
    }
  }
}
