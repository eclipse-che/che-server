/*
 * Copyright (c) 2012-2022 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.factory.server.scm;

import java.util.Objects;

/** Personal SCM user data such as `username` and `email`. Is used to sign git commits. */
public class GitUserData {
  private final String scmUsername;
  private final String scmUserEmail;

  public GitUserData(String scmUsername, String scmUserEmail) {
    this.scmUsername = scmUsername;
    this.scmUserEmail = scmUserEmail;
  }

  public String getScmUsername() {
    return scmUsername;
  }

  public String getScmUserEmail() {
    return scmUserEmail;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GitUserData that = (GitUserData) o;
    return Objects.equals(scmUsername, that.scmUsername)
        && Objects.equals(scmUserEmail, that.scmUserEmail);
  }

  @Override
  public int hashCode() {
    return Objects.hash(scmUsername, scmUserEmail);
  }

  @Override
  public String toString() {
    return "GitUserData{"
        + ", scmUsername='"
        + scmUsername
        + '\''
        + ", scmUserEmail='"
        + scmUserEmail
        + '\''
        + '}';
  }
}
