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
package org.eclipse.che.multiuser.api.permission.server.filter.check;

import static org.eclipse.che.multiuser.api.permission.server.AbstractPermissionsDomain.SET_PERMISSIONS;

import javax.inject.Singleton;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.commons.env.EnvironmentContext;

/**
 * Common checks while removing permissions.
 *
 * @author Anton Korneta
 */
@Singleton
public class DefaultRemovePermissionsChecker implements RemovePermissionsChecker {

  @Override
  public void check(String user, String domainId, String instance) throws ForbiddenException {
    if (!EnvironmentContext.getCurrent()
        .getSubject()
        .hasPermission(domainId, instance, SET_PERMISSIONS)) {
      throw new ForbiddenException("User can't edit permissions for this instance");
    }
  }
}
