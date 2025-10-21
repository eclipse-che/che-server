/*
 * Copyright (c) 2012-2024 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.user.server;

import static java.util.Collections.emptyList;

import jakarta.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.account.api.AccountManager;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.user.server.model.impl.UserImpl;

/**
 * Creates 'che' default user.
 *
 * @author Anton Korneta
 */
@Deprecated
@Singleton
public class CheUserCreator {

  @Inject private UserManager userManager;

  @Inject private AccountManager accountManager;

  @PostConstruct
  public void createCheUser() throws ServerException {
    try {
      userManager.getById("che");
    } catch (NotFoundException ex) {
      try {
        final UserImpl cheUser =
            new UserImpl("che", "che@eclipse.org", "che", "secret", emptyList());
        userManager.create(cheUser, false);
      } catch (ConflictException ignore) {
      }
    }
  }
}
