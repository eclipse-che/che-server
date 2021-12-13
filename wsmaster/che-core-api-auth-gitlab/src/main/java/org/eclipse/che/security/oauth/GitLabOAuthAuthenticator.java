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

@Singleton
public class GitLabOAuthAuthenticator extends OAuthAuthenticator {
  private static final String GITLAB_API_USER_PATH = "/api/v4/user";
  private final String gitlabEndpoint;

  public GitLabOAuthAuthenticator(
      String clientId, String clientSecret, String gitlabEndpoint, String[] redirectUris)
      throws IOException {
    this.gitlabEndpoint = gitlabEndpoint;
    configure(
        clientId,
        clientSecret,
        redirectUris,
        gitlabEndpoint + "/oauth/authorize",
        gitlabEndpoint + "/oauth/token",
        new MemoryDataStoreFactory());
  }

  @Override
  public User getUser(OAuthToken accessToken) throws OAuthAuthenticationException {
    GitLabUser user =
        getJson(gitlabEndpoint + GITLAB_API_USER_PATH, accessToken.getToken(), GitLabUser.class);
    final String email = user.getEmail();

    if (isNullOrEmpty(email)) {
      throw new OAuthAuthenticationException(
          "Sorry, we failed to find any verified email associated with your GitLab account."
              + " Please, verify at least one email in your account and try to connect with GitLab again.");
    }
    try {
      new InternetAddress(email).validate();
    } catch (AddressException e) {
      throw new OAuthAuthenticationException(e.getMessage());
    }
    return user;
  }

  @Override
  public String getOAuthProvider() {
    return "gitlab";
  }

  @Override
  public OAuthToken getToken(String userId) throws IOException {
    final OAuthToken token = super.getToken(userId);
    try {
      if (token == null
          || token.getToken() == null
          || token.getToken().isEmpty()
          || getJson(gitlabEndpoint + GITLAB_API_USER_PATH, token.getToken(), GitLabUser.class)
              == null) {
        return null;
      }
    } catch (OAuthAuthenticationException e) {
      return null;
    }
    return token;
  }
}
