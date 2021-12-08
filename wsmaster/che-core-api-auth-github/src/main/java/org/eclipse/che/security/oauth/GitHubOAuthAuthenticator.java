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
    // Need to check if token which is stored is valid for requests, then if valid - we returns it
    // to
    // caller
    try {
      if (token == null
          || token.getToken() == null
          || token.getToken().isEmpty()
          || getJson("https://api.github.com/user", token.getToken(), GitHubUser.class) == null) {
        return null;
      }
    } catch (OAuthAuthenticationException e) {
      return null;
    }
    return token;
  }
}
