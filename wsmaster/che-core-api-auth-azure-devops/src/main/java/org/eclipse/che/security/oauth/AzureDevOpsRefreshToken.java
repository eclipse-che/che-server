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
package org.eclipse.che.security.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AzureDevOpsRefreshToken {
  /** Access token issued by the authorization server. */
  private String accessToken;

  /** Token type. */
  private String tokenType;

  /** Refresh token which can be used to obtain new access tokens. */
  private String refreshToken;

  /**
   * Lifetime in seconds of the access token (for example 3600 for an hour) or {@code null} for
   * none.
   */
  private String expiresInSeconds;

  /** Scope of the access token. */
  private String scope;

  public String getAccessToken() {
    return accessToken;
  }

  public String getTokenType() {
    return tokenType;
  }

  public String getRefreshToken() {
    return refreshToken;
  }

  public String getScope() {
    return scope;
  }

  public String getExpiresInSeconds() {
    return expiresInSeconds;
  }

  @JsonProperty("access_token")
  public void setAccessToken(String accessToken) {
    this.accessToken = accessToken;
  }

  @JsonProperty("token_type")
  public void setTokenType(String tokenType) {
    this.tokenType = tokenType;
  }

  @JsonProperty("refresh_token")
  public void setRefreshToken(String refreshToken) {
    this.refreshToken = refreshToken;
  }

  @JsonProperty("expires_in")
  public void setExpiresInSeconds(String expiresInSeconds) {
    this.expiresInSeconds = expiresInSeconds;
  }

  public void setScope(String scope) {
    this.scope = scope;
  }

  @Override
  public String toString() {
    return "AzureDevOpsRefreshToken{"
        + "accessToken='"
        + accessToken
        + '\''
        + ", tokenType='"
        + tokenType
        + '\''
        + ", refreshToken='"
        + refreshToken
        + '\''
        + ", expiresInSeconds='"
        + expiresInSeconds
        + '\''
        + ", scope='"
        + scope
        + '\''
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AzureDevOpsRefreshToken that = (AzureDevOpsRefreshToken) o;
    return Objects.equals(accessToken, that.accessToken)
        && Objects.equals(tokenType, that.tokenType)
        && Objects.equals(refreshToken, that.refreshToken)
        && Objects.equals(expiresInSeconds, that.expiresInSeconds)
        && Objects.equals(scope, that.scope);
  }

  @Override
  public int hashCode() {
    return Objects.hash(accessToken, tokenType, refreshToken, expiresInSeconds, scope);
  }
}
