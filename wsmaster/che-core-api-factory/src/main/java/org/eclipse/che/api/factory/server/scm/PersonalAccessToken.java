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
  private final String scmUserName;
  /** Organization that user belongs to. Can be null if user is not a member of any organization. */
  @Nullable private final String scmOrganization;

  private final String scmTokenName;
  private final String scmTokenId;
  private final String token;
  private final String cheUserId;

  public PersonalAccessToken(
      String scmProviderUrl,
      String cheUserId,
      String scmOrganization,
      String scmUserName,
      String scmTokenName,
      String scmTokenId,
      String token) {
    this.scmProviderUrl = scmProviderUrl;
    this.scmOrganization = scmOrganization;
    this.scmUserName = scmUserName;
    this.scmTokenName = scmTokenName;
    this.scmTokenId = scmTokenId;
    this.token = token;
    this.cheUserId = cheUserId;
  }

  public PersonalAccessToken(
      String scmProviderUrl,
      String cheUserId,
      String scmUserName,
      String scmTokenName,
      String scmTokenId,
      String token) {
    this(scmProviderUrl, cheUserId, null, scmUserName, scmTokenName, scmTokenId, token);
  }

  public PersonalAccessToken(String scmProviderUrl, String scmUserName, String token) {
    this(
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

  public String getCheUserId() {
    return cheUserId;
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
    return Objects.equal(scmProviderUrl, that.scmProviderUrl)
        && Objects.equal(scmUserName, that.scmUserName)
        && Objects.equal(scmOrganization, that.scmOrganization)
        && Objects.equal(scmTokenName, that.scmTokenName)
        && Objects.equal(scmTokenId, that.scmTokenId)
        && Objects.equal(token, that.token)
        && Objects.equal(cheUserId, that.cheUserId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        scmProviderUrl, scmUserName, scmOrganization, scmTokenName, scmTokenId, token, cheUserId);
  }

  @Override
  public String toString() {
    return "PersonalAccessToken{"
        + "scmProviderUrl='"
        + scmProviderUrl
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
        + ", cheUserId='"
        + cheUserId
        + '}';
  }
}
