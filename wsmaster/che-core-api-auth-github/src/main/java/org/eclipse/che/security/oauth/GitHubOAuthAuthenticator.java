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
package org.eclipse.che.security.oauth;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.eclipse.che.commons.lang.StringUtils.trimEnd;

import com.google.api.client.util.store.MemoryDataStoreFactory;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import javax.inject.Singleton;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.security.oauth.shared.User;

/** OAuth authentication for github account. */
@Singleton
public class GitHubOAuthAuthenticator extends OAuthAuthenticator {
  private final String clientId;
  private final String clientSecret;
  private final String githubApiUrl;
  private final String providerUrl;

  public GitHubOAuthAuthenticator(
      String clientId,
      String clientSecret,
      String[] redirectUris,
      String authEndpoint,
      String authUri,
      String tokenUri)
      throws IOException {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    providerUrl = isNullOrEmpty(authEndpoint) ? "https://github.com" : trimEnd(authEndpoint, '/');
    githubApiUrl =
        providerUrl.equals("https://github.com")
            ? "https://api.github.com"
            : providerUrl + "/api/v3";
    configure(
        clientId, clientSecret, redirectUris, authUri, tokenUri, new MemoryDataStoreFactory());
  }

  @Override
  public User getUser(OAuthToken accessToken) throws OAuthAuthenticationException {
    GitHubUser user = getJson(githubApiUrl + "/user", accessToken.getToken(), GitHubUser.class);
    final String email = user.getEmail();

    if (isNullOrEmpty(email)) {
      throw new OAuthAuthenticationException(
          "Sorry, we failed to find any verified emails associated with your GitHub account."
              + " Please, verify at least one email in your GitHub account and try to connect with GitHub again.");
    }
    try {
      new InternetAddress(email).validate();
    } catch (AddressException e) {
      throw new OAuthAuthenticationException(e.getMessage());
    }
    return user;
  }

  @Override
  public final String getOAuthProvider() {
    return "github";
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
  public OAuthToken getToken(String oauthProvider) throws IOException {
    final OAuthToken token = super.getToken(oauthProvider);
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
