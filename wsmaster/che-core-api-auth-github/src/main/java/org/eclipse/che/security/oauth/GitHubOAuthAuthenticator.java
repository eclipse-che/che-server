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
package org.eclipse.che.security.oauth;

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.api.client.util.store.MemoryDataStoreFactory;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.inject.Singleton;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.security.oauth.shared.User;

/** OAuth authentication for github account. */
@Singleton
public class GitHubOAuthAuthenticator extends OAuthAuthenticator {
  public GitHubOAuthAuthenticator(
      String clientId, String clientSecret, String[] redirectUris, String authUri, String tokenUri)
      throws IOException {
    configure(
        clientId, clientSecret, redirectUris, authUri, tokenUri, new MemoryDataStoreFactory());
  }

  @Override
  public User getUser(OAuthToken accessToken) throws OAuthAuthenticationException {
    GitHubUser user =
        getJson("https://api.github.com/user", accessToken.getToken(), GitHubUser.class);
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
  public OAuthToken getToken(String userId) throws IOException {
    final OAuthToken token = super.getToken(userId);
    if (!(token == null || token.getToken() == null || token.getToken().isEmpty())) {
      // Need to check if token which stored is valid for requests, then if valid - we returns it to
      // caller
      String tokenVerifyUrl = "https://api.github.com/user";
      HttpURLConnection http = null;
      try {
        http = (HttpURLConnection) new URL(tokenVerifyUrl).openConnection();
        http.setInstanceFollowRedirects(false);
        http.setRequestMethod("GET");
        http.setRequestProperty("Accept", "application/json");
        http.setRequestProperty("Authorization", "token " + token.getToken());

        if (http.getResponseCode() == 401) {
          return null;
        }
      } finally {
        if (http != null) {
          http.disconnect();
        }
      }

      return token;
    }
    return null;
  }
}
