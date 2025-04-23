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
package org.eclipse.che.workspace.infrastructure.kubernetes.multiuser.oauth;

import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.commons.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sets up implementation of {@link Subject} that can check permissions.
 *
 * @author Sergii Leschenko
 */
public class AuthorizedSubject implements Subject {

  private static final Logger LOG = LoggerFactory.getLogger(AuthorizedSubject.class);

  private final Subject baseSubject;

  public AuthorizedSubject(Subject baseSubject) {
    this.baseSubject = baseSubject;
  }

  @Override
  public String getUserName() {
    return baseSubject.getUserName();
  }

  @Override
  public boolean hasPermission(String domain, String instance, String action) {
    return true;
  }

  @Override
  public void checkPermission(String domain, String instance, String action)
      throws ForbiddenException {
    if (!hasPermission(domain, instance, action)) {
      String message = "User is not authorized to perform " + action + " of " + domain;
      if (instance != null) {
        message += " with id '" + instance + "'";
      }
      throw new ForbiddenException(message);
    }
  }

  @Override
  public String getToken() {
    return baseSubject.getToken();
  }

  @Override
  public String getUserId() {
    return baseSubject.getUserId();
  }

  @Override
  public boolean isTemporary() {
    return baseSubject.isTemporary();
  }
}
