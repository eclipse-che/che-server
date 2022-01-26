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
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.inject.Singleton;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.commons.json.JsonHelper;
import org.eclipse.che.commons.json.JsonParseException;
import org.eclipse.che.security.oauth.shared.User;

/**
 * OAuth2 authenticator for GitLab account.
 *
 * @author Pavol Baran
 */
@Singleton
public class GitLabOAuthAuthenticator extends OAuthAuthenticator {
  private final String gitlabUserEndpoint;
  private final String cheApiEndpoint;

  public GitLabOAuthAuthenticator(
      String clientId, String clientSecret, String gitlabEndpoint, String cheApiEndpoint)
      throws IOException {
    this.gitlabUserEndpoint = gitlabEndpoint + "/api/v4/user";
    this.cheApiEndpoint = cheApiEndpoint;
    configure(
        clientId,
        clientSecret,
        new String[] {},
        gitlabEndpoint + "/oauth/authorize",
        gitlabEndpoint + "/oauth/token",
        new MemoryDataStoreFactory());
  }

  @Override
  public User getUser(OAuthToken accessToken) throws OAuthAuthenticationException {
    GitLabUser user = getJson(gitlabUserEndpoint, accessToken.getToken(), GitLabUser.class);
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
  protected String findRedirectUrl(URL requestUrl) {
    return cheApiEndpoint + "/oauth/callback";
  }

  @Override
  protected <O> O getJson(String getUserUrl, String accessToken, Class<O> userClass)
      throws OAuthAuthenticationException {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(getUserUrl))
            .header("Authorization", "Bearer " + accessToken)
            .build();

    try {
      HttpResponse<InputStream> response =
          client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      return JsonHelper.fromJson(response.body(), userClass, null);
    } catch (IOException | InterruptedException | JsonParseException e) {
      throw new OAuthAuthenticationException(e.getMessage(), e);
    }
  }

  @Override
  public OAuthToken getToken(String userId) throws IOException {
    final OAuthToken token = super.getToken(userId);
    try {
      if (token == null
          || token.getToken() == null
          || token.getToken().isEmpty()
          || getJson(gitlabUserEndpoint, token.getToken(), GitLabUser.class) == null) {
        return null;
      }
    } catch (OAuthAuthenticationException e) {
      return null;
    }
    return token;
  }
}
