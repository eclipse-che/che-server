/*
 * Copyright (c) 2012-2026 Red Hat, Inc.
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

import com.google.common.base.Objects;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.env.EnvironmentContext;

/**
 * Personal access token that can be used to authorise scm operations like api calls, git clone or
 * git push.
 */
public class PersonalAccessToken {

  private final String scmProviderUrl;
  private final String scmProviderName;
  private final String scmUserName;

  /** Organization that user belongs to. Can be null if user is not a member of any organization. */
  @Nullable private final String scmOrganization;

  /** OAuth refresh token for obtaining new access tokens. Null for non-OAuth (PAT) tokens. */
  @Nullable private final String refreshToken;

  /** Token expiration time in seconds. 0 if the token does not expire. */
  @Nullable private final long expiresIn;

  private final String scmTokenName;
  private final String scmTokenId;
  private final String token;
  private final String cheUserId;

  public PersonalAccessToken(
      String scmProviderUrl,
      String scmProviderName,
      String cheUserId,
      String scmOrganization,
      String scmUserName,
      String scmTokenName,
      String scmTokenId,
      String token,
      String refreshToken,
      long expiresIn) {
    this.scmProviderUrl = scmProviderUrl;
    this.scmOrganization = scmOrganization;
    this.scmProviderName = scmProviderName;
    this.scmUserName = scmUserName;
    this.scmTokenName = scmTokenName;
    this.scmTokenId = scmTokenId;
    this.token = token;
    this.refreshToken = refreshToken;
    this.cheUserId = cheUserId;
    this.expiresIn = expiresIn;
  }

  public PersonalAccessToken(
      String scmProviderUrl, String scmProviderName, String scmUserName, String token) {
    this(
        scmProviderUrl,
        scmProviderName,
        EnvironmentContext.getCurrent().getSubject().getUserId(),
        null,
        scmUserName,
        null,
        null,
        token,
        null,
        0);
  }

  public String getScmProviderUrl() {
    return scmProviderUrl;
  }

  public String getScmTokenName() {
    return scmTokenName;
  }

  public String getScmTokenId() {
    return scmTokenId;
  }

  public String getScmUserName() {
    return scmUserName;
  }

  public String getToken() {
    return token;
  }

  @Nullable
  public String getRefreshToken() {
    return refreshToken;
  }

  public String getCheUserId() {
    return cheUserId;
  }

  @Nullable
  public String getScmOrganization() {
    return scmOrganization;
  }

  @Nullable
  public long getExpiresIn() {
    return expiresIn;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PersonalAccessToken that = (PersonalAccessToken) o;
    return Objects.equal(scmProviderUrl, that.scmProviderUrl)
        && Objects.equal(scmProviderName, that.scmProviderName)
        && Objects.equal(scmUserName, that.scmUserName)
        && Objects.equal(scmOrganization, that.scmOrganization)
        && Objects.equal(scmTokenName, that.scmTokenName)
        && Objects.equal(scmTokenId, that.scmTokenId)
        && Objects.equal(token, that.token)
        && Objects.equal(refreshToken, that.refreshToken)
        && Objects.equal(cheUserId, that.cheUserId)
        && Objects.equal(expiresIn, that.expiresIn);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        scmProviderUrl,
        scmUserName,
        scmOrganization,
        scmTokenName,
        scmTokenId,
        token,
        refreshToken,
        cheUserId,
        expiresIn);
  }

  @Override
  public String toString() {
    return "PersonalAccessToken{"
        + "scmProviderUrl='"
        + scmProviderUrl
        + '\''
        + "scmProviderName='"
        + scmProviderName
        + '\''
        + ", scmUserName='"
        + scmUserName
        + '\''
        + ", scmOrganization='"
        + scmOrganization
        + '\''
        + ", scmTokenName='"
        + scmTokenName
        + '\''
        + ", scmTokenId='"
        + scmTokenId
        + '\''
        + ", token='"
        + token
        + '\''
        + ", refreshToken='"
        + refreshToken
        + '\''
        + ", cheUserId='"
        + cheUserId
        + '\''
        + ", expiresIn="
        + expiresIn
        + '}';
  }

  public String getScmProviderName() {
    return scmProviderName;
  }
}
