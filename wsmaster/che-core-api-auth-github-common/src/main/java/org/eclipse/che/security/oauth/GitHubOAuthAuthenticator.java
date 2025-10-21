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

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.eclipse.che.commons.lang.StringUtils.trimEnd;

import com.google.api.client.util.store.MemoryDataStoreFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;

/** OAuth authentication for github account. */
public class GitHubOAuthAuthenticator extends OAuthAuthenticator {
  private final String clientId;
  private final String clientSecret;
  private final String githubApiUrl;
  private final String providerUrl;
  private final String providerName;

  public GitHubOAuthAuthenticator(
      String clientId,
      String clientSecret,
      String[] redirectUris,
      String authEndpoint,
      String authUri,
      String tokenUri,
      String providerName)
      throws IOException {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.providerName = providerName;
    providerUrl = isNullOrEmpty(authEndpoint) ? "https://github.com" : trimEnd(authEndpoint, '/');
    githubApiUrl =
        providerUrl.equals("https://github.com")
            ? "https://api.github.com"
            : providerUrl + "/api/v3";
    configure(
        clientId, clientSecret, redirectUris, authUri, tokenUri, new MemoryDataStoreFactory());
  }

  @Override
  public final String getOAuthProvider() {
    return providerName;
  }

  @Override
  public boolean invalidateToken(String token) throws IOException {
    HttpURLConnection urlConnection = null;
    try {
      String creds = clientId + ":" + clientSecret;
      String basicAuth = "Basic " + new String(Base64.getEncoder().encode(creds.getBytes()));
      String jsonInputString = String.format("{\"access_token\":\"%s\"}", token);
      urlConnection =
          (HttpURLConnection)
              new URL(String.format("%s/applications/%s/grant", githubApiUrl, clientId))
                  .openConnection();
      urlConnection.setRequestMethod("DELETE");
      urlConnection.setRequestProperty("Authorization", basicAuth);
      urlConnection.setDoOutput(true);
      try (OutputStream os = urlConnection.getOutputStream()) {
        os.write(jsonInputString.getBytes());
      }
      if (urlConnection.getResponseCode() != 204) {
        return false;
      }
    } finally {
      if (urlConnection != null) {
        urlConnection.disconnect();
      }
    }
    return true;
  }

  @Override
  public OAuthToken getOrRefreshToken(String oauthProvider) throws IOException {
    final OAuthToken token = super.getOrRefreshToken(oauthProvider);
    // Need to check if token which is stored is valid for requests, then if valid - we returns it
    // to
    // caller
    try {
      if (token == null
          || token.getToken() == null
          || token.getToken().isEmpty()
          || getJson(githubApiUrl + "/user", token.getToken(), GitHubUser.class) == null) {
        return null;
      }
    } catch (OAuthAuthenticationException e) {
      return null;
    }
    return token;
  }

  public String getEndpointUrl() {
    return providerUrl;
  }
}
