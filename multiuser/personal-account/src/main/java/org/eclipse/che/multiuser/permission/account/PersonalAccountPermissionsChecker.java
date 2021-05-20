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
package org.eclipse.che.multiuser.permission.account;

import javax.inject.Singleton;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.multiuser.api.permission.server.account.AccountOperation;
import org.eclipse.che.multiuser.api.permission.server.account.AccountPermissionsChecker;

/**
 * Defines permissions checking for personal accounts.
 *
 * <p>Throws exception during permissions checking when user tries to perform any operation with
 * foreign personal account.
 *
 * @author Sergii Leshchenko
 */
@Singleton
public class PersonalAccountPermissionsChecker implements AccountPermissionsChecker {
  @Override
  public void checkPermissions(String id, AccountOperation operation) throws ForbiddenException {
    // ignore action because user should be able to do anything in his personal account
    if (!EnvironmentContext.getCurrent().getSubject().getUserId().equals(id)) {
      throw new ForbiddenException("User is not authorized to use specified account");
    }
  }

  @Override
  public String getAccountType() {
    return UserManager.PERSONAL_ACCOUNT;
  }
}
