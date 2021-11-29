/*
 * Copyright (c) 2012-2021 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.multiuser.oidc;

import java.util.Optional;
import java.util.StringJoiner;

/** OIDCInfo - POJO object to store information about OIDC api. */
public class OIDCInfo {

  private final String tokenPublicEndpoint;
  private final String endSessionPublicEndpoint;
  private final String userInfoPublicEndpoint;
  private final String userInfoInternalEndpoint;
  private final String jwksPublicUri;
  private final String jwksInternalUri;
  private final String authServerURL;
  private final String authServerPublicURL;

  public OIDCInfo(
      String tokenPublicEndpoint,
      String endSessionPublicEndpoint,
      String userInfoPublicEndpoint,
      String userInfoInternalEndpoint,
      String jwksPublicUri,
      String jwksInternalUri,
      String authServerURL,
      String authServerPublicURL) {
    this.tokenPublicEndpoint = tokenPublicEndpoint;
    this.endSessionPublicEndpoint = endSessionPublicEndpoint;
    this.userInfoPublicEndpoint = userInfoPublicEndpoint;
    this.userInfoInternalEndpoint = userInfoInternalEndpoint;
    this.jwksPublicUri = jwksPublicUri;
    this.jwksInternalUri = jwksInternalUri;
    this.authServerURL = authServerURL;
    this.authServerPublicURL = authServerPublicURL;
  }

  /** @return public url to retrieve token */
  public String getTokenPublicEndpoint() {
    return tokenPublicEndpoint;
  }

  /** @return public url to get user profile information. */
  public String getUserInfoPublicEndpoint() {
    return userInfoPublicEndpoint;
  }

  /** @return internal network url to get user profile information. */
  public String getUserInfoInternalEndpoint() {
    return userInfoInternalEndpoint;
  }

  /** @return public url to retrieve JWK public key for token validation. */
  public String getJwksPublicUri() {
    return jwksPublicUri;
  }

  /** @return internal network url to retrieve JWK public key for token validation. */
  public String getJwksInternalUri() {
    return jwksInternalUri;
  }

  /**
   * @return OIDC auth endpoint url. Url will be internal if internal network enabled, otherwise url
   *     will be public.
   */
  public String getAuthServerURL() {
    return authServerURL;
  }

  /** @return public OIDC auth endpoint url. */
  public String getAuthServerPublicURL() {
    return authServerPublicURL;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", OIDCInfo.class.getSimpleName() + "[", "]")
        .add("tokenPublicEndpoint='" + tokenPublicEndpoint + "'")
        .add("userInfoPublicEndpoint='" + userInfoPublicEndpoint + "'")
        .add("userInfoInternalEndpoint='" + userInfoInternalEndpoint + "'")
        .add("jwksPublicUri='" + jwksPublicUri + "'")
        .add("jwksInternalUri='" + jwksInternalUri + "'")
        .add("authServerURL='" + authServerURL + "'")
        .add("authServerPublicURL='" + authServerPublicURL + "'")
        .toString();
  }

  public Optional<String> getEndSessionPublicEndpoint() {
    return Optional.ofNullable(endSessionPublicEndpoint);
  }
}
