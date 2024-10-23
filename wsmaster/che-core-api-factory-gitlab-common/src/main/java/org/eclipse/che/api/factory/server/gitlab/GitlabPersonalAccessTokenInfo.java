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
package org.eclipse.che.api.factory.server.gitlab;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Arrays;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GitlabPersonalAccessTokenInfo {

  private int id;
  private String[] scopes;
  private String expires_at;
  private String created_at;

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public String[] getScopes() {
    return scopes;
  }

  public void setScopes(String[] scopes) {
    this.scopes = scopes;
  }

  public String getExpires_at() {
    return expires_at;
  }

  public void setExpires_at(String expires_at) {
    this.expires_at = expires_at;
  }

  public String getCreated_at() {
    return created_at;
  }

  public void setCreated_at(String created_at) {
    this.created_at = created_at;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GitlabPersonalAccessTokenInfo info = (GitlabPersonalAccessTokenInfo) o;
    return id == info.id
        && Objects.equals(expires_at, info.expires_at)
        && created_at == info.created_at
        && Arrays.equals(scopes, info.scopes);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(id, expires_at, created_at);
    result = 31 * result + Arrays.hashCode(scopes);
    return result;
  }

  @Override
  public String toString() {
    return "GitlabOauthTokenInfo{"
        + "resource_owner_id="
        + id
        + ", scope="
        + Arrays.toString(scopes)
        + ", expires_at="
        + expires_at
        + ", created_at="
        + created_at
        + '}';
  }
}
