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
package org.eclipse.che.api.user.server.event;

import org.eclipse.che.api.core.model.user.User;
import org.eclipse.che.api.core.notification.EventOrigin;
import org.eclipse.che.api.user.server.model.impl.UserImpl;

/**
 * Will be published when {@link UserImpl user} is created.
 *
 * @author Anatolii Bazko
 */
@EventOrigin("user")
public class UserCreatedEvent {

  private final User user;

  public UserCreatedEvent(User user) {
    this.user = user;
  }

  public User getUser() {
    return user;
  }
}
