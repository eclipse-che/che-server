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
package org.eclipse.che.commons.subject;

import org.eclipse.che.api.core.ForbiddenException;

/**
 * Subject represents authenticated user
 *
 * @author andrew00x
 * @author Sergii Leschenko
 */
public interface Subject {
  /** Unidentified subject */
  Subject ANONYMOUS =
      new Subject() {
        @Override
        public String getUserId() {
          return "0000-00-0000";
        }

        @Override
        public String getUserName() {
          return "Anonymous";
        }

        @Override
        public boolean hasPermission(String domain, String instance, String action) {
          return false;
        }

        @Override
        public void checkPermission(String domain, String instance, String action)
            throws ForbiddenException {
          throw new ForbiddenException(
              "User is not authorized to perform "
                  + action
                  + " of "
                  + domain
                  + " with id '"
                  + instance
                  + "'");
        }

        @Override
        public String getToken() {
          return null;
        }

        @Override
        public boolean isAnonymous() {
          return true;
        }

        @Override
        public boolean isTemporary() {
          return false;
        }
      };

  /**
   * Get user unique identifier.
   *
   * <p>Note: In comparison with name id never changes for the given user.
   *
   * @return unique identifier of user.
   */
  String getUserId();

  /**
   * @return name of user
   */
  String getUserName();

  /**
   * Checks does subject have specified permission.
   *
   * @return {@code true} if subject has permission to perform given action and {@code false}
   *     otherwise
   */
  boolean hasPermission(String domain, String instance, String action);

  /**
   * Ensures this Subject has specified permission.
   *
   * @throws ForbiddenException if subject doesn't have specified permission
   */
  void checkPermission(String domain, String instance, String action) throws ForbiddenException;

  /**
   * @return subject auth token to be able to execute request as subject
   */
  String getToken();

  /**
   * Return {@code true} if subject is anonymous, {@code false} if this is a real authenticated
   * subject.
   */
  default boolean isAnonymous() {
    return false;
  }

  /**
   * @return - true if subject is temporary, false if this is a real persistent subject.
   */
  boolean isTemporary();
}
