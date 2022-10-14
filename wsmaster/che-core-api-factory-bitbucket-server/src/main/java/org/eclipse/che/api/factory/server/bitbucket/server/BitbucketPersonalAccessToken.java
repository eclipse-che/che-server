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
package org.eclipse.che.api.factory.server.bitbucket.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Objects;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketPersonalAccessToken {
  private long id;
  private long createdDate;
  private long lastAuthenticated;
  private int expiryDays;
  private String name;
  private String token;
  private BitbucketUser user;
  private Set<String> permissions;

  public BitbucketPersonalAccessToken(String name, Set<String> permissions, int expiryDays) {
    this.name = name;
    this.permissions = permissions;
    this.expiryDays = expiryDays;
  }

  public BitbucketPersonalAccessToken() {}

  public BitbucketPersonalAccessToken(
      long id,
      long createdDate,
      long lastAuthenticated,
      int expiryDays,
      String name,
      String token,
      BitbucketUser user,
      Set<String> permissions) {
    this.id = id;
    this.createdDate = createdDate;
    this.lastAuthenticated = lastAuthenticated;
    this.expiryDays = expiryDays;
    this.name = name;
    this.token = token;
    this.user = user;
    this.permissions = permissions;
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getCreatedDate() {
    return createdDate;
  }

  public void setCreatedDate(long createdDate) {
    this.createdDate = createdDate;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public BitbucketUser getUser() {
    return user;
  }

  public void setUser(BitbucketUser user) {
    this.user = user;
  }

  public Set<String> getPermissions() {
    return permissions;
  }

  public void setPermissions(Set<String> permissions) {
    this.permissions = permissions;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public long getLastAuthenticated() {
    return lastAuthenticated;
  }

  public void setLastAuthenticated(long lastAuthenticated) {
    this.lastAuthenticated = lastAuthenticated;
  }

  public long getExpiryDays() {
    return expiryDays;
  }

  public void setExpiryDays(int expiryDays) {
    this.expiryDays = expiryDays;
  }

  @Override
  public String toString() {
    return "BitbucketPersonalAccessToken{"
        + "id="
        + id
        + ", createdDate="
        + createdDate
        + ", lastAuthenticated="
        + lastAuthenticated
        + ", expiryDate="
        + expiryDays
        + ", name='"
        + name
        + '\''
        + ", token='"
        + token
        + '\''
        + ", user="
        + user
        + ", permissions="
        + permissions
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BitbucketPersonalAccessToken that = (BitbucketPersonalAccessToken) o;
    return id == that.id
        && createdDate == that.createdDate
        && lastAuthenticated == that.lastAuthenticated
        && expiryDays == that.expiryDays
        && Objects.equals(name, that.name)
        && Objects.equals(token, that.token)
        && Objects.equals(user, that.user)
        && Objects.equals(permissions, that.permissions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id, createdDate, lastAuthenticated, expiryDays, name, token, user, permissions);
  }
}
