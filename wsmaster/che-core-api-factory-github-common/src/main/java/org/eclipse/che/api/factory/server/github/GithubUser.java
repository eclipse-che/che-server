/*
 * Copyright (c) 2012-2023 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.factory.server.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GithubUser {

  private long id;
  private String login;
  private String email;
  private String name;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public GithubUser withId(long id) {
    this.id = id;
    return this;
  }

  public String getLogin() {
    return login;
  }

  public void setLogin(String login) {
    this.login = login;
  }

  public GithubUser withLogin(String login) {
    this.login = login;
    return this;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public GithubUser withEmail(String email) {
    this.email = email;
    return this;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public GithubUser withName(String name) {
    this.name = name;
    return this;
  }

  @Override
  public String toString() {
    return "GithubUser{"
        + "id="
        + id
        + ", login='"
        + login
        + '\''
        + ", email='"
        + email
        + '\''
        + ", name='"
        + name
        + '\''
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GithubUser that = (GithubUser) o;
    return id == that.id
        && Objects.equals(login, that.login)
        && Objects.equals(email, that.email)
        && Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, login, email, name);
  }
}
