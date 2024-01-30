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
package org.eclipse.che.api.factory.server.scm;

import com.google.common.base.Objects;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.env.EnvironmentContext;

/**
 * Personal access token that can be used to authorise scm operations like api calls, git clone or
 * git push.
 */
public class PersonalAccessToken {

  private final boolean isOAuthToken;
  private final String scmProviderUrl;
  private final String scmUserName;
  /** Organization that user belongs to. Can be null if user is not a member of any organization. */
  @Nullable private final String scmOrganization;

  private final String scmProviderName;
  private final String scmTokenId;
  private final String token;
  private final String cheUserId;

  public PersonalAccessToken(
      boolean isOAuthToken,
      String scmProviderUrl,
      String cheUserId,
      String scmOrganization,
      String scmUserName,
      String scmProviderName,
      String scmTokenId,
      String token) {
    this.isOAuthToken = isOAuthToken;
    this.scmProviderUrl = scmProviderUrl;
    this.scmOrganization = scmOrganization;
    this.scmUserName = scmUserName;
    this.scmProviderName = scmProviderName;
    this.scmTokenId = scmTokenId;
    this.token = token;
    this.cheUserId = cheUserId;
  }

  public PersonalAccessToken(
      String scmProviderUrl,
      String cheUserId,
      String scmUserName,
      String scmProviderName,
      String scmTokenId,
      String token) {
    this(true, scmProviderUrl, cheUserId, null, scmUserName, scmProviderName, scmTokenId, token);
  }

  public PersonalAccessToken(String scmProviderUrl, String scmUserName, String token) {
    this(
        true,
        scmProviderUrl,
        EnvironmentContext.getCurrent().getSubject().getUserId(),
        null,
        scmUserName,
        null,
        null,
        token);
  }

  public String getScmProviderUrl() {
    return scmProviderUrl;
  }

  public String getScmProviderName() {
    return scmProviderName;
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

  public String getCheUserId() {
    return cheUserId;
  }

  public boolean isOAuthToken() {
    return isOAuthToken;
  }

  @Nullable
  public String getScmOrganization() {
    return scmOrganization;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PersonalAccessToken that = (PersonalAccessToken) o;
    return Objects.equal(isOAuthToken, that.isOAuthToken)
        && Objects.equal(scmProviderUrl, that.scmProviderUrl)
        && Objects.equal(scmUserName, that.scmUserName)
        && Objects.equal(scmOrganization, that.scmOrganization)
        && Objects.equal(scmProviderName, that.scmProviderName)
        && Objects.equal(scmTokenId, that.scmTokenId)
        && Objects.equal(token, that.token)
        && Objects.equal(cheUserId, that.cheUserId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        isOAuthToken,
        scmProviderUrl,
        scmUserName,
        scmOrganization,
        scmProviderName,
        scmTokenId,
        token,
        cheUserId);
  }

  @Override
  public String toString() {
    return "PersonalAccessToken{"
        + "isOAuthToken="
        + isOAuthToken
        + ", scmProviderUrl='"
        + scmProviderUrl
        + '\''
        + ", scmUserName='"
        + scmUserName
        + '\''
        + ", scmOrganization='"
        + scmOrganization
        + '\''
        + ", scmProviderName='"
        + scmProviderName
        + '\''
        + ", scmTokenId='"
        + scmTokenId
        + '\''
        + ", token='"
        + token
        + '\''
        + ", cheUserId='"
        + cheUserId
        + '\''
        + '}';
  }
}
