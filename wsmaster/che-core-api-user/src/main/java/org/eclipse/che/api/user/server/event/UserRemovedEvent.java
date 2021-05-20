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

import org.eclipse.che.api.core.notification.EventOrigin;
import org.eclipse.che.api.user.server.model.impl.UserImpl;

/**
 * Published after {@link UserImpl user} removed.
 *
 * @author Sergii Kabashniuk
 */
@EventOrigin("user")
public class UserRemovedEvent {

  private final String userId;

  public UserRemovedEvent(String userId) {
    this.userId = userId;
  }

  /** Returns id of removed user */
  public String getUserId() {
    return userId;
  }
}
