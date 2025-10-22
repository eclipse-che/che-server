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
import static java.lang.String.format;
import static org.eclipse.che.commons.lang.StringUtils.trimEnd;

import com.google.api.client.util.store.MemoryDataStoreFactory;
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

/**
 * OAuth2 authenticator for GitLab account.
 *
 * @author Pavol Baran
 */
@Singleton
public class GitLabOAuthAuthenticator extends OAuthAuthenticator {
  private final String gitlabUserEndpoint;
  private final String cheApiEndpoint;
  private final String clientId;
  private final String clientSecret;
  private final String gitlabEndpoint;
  private final String providerName;

  public GitLabOAuthAuthenticator(
      String clientId,
      String clientSecret,
      String gitlabEndpoint,
      String cheApiEndpoint,
      String providerName)
      throws IOException {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.gitlabEndpoint = trimEnd(gitlabEndpoint, '/');
    this.providerName = providerName;
    String trimmedGitlabEndpoint = trimEnd(gitlabEndpoint, '/');
    this.gitlabUserEndpoint = trimmedGitlabEndpoint + "/api/v4/user";
    this.cheApiEndpoint = cheApiEndpoint;
    configure(
        clientId,
        clientSecret,
        new String[] {},
        trimmedGitlabEndpoint + "/oauth/authorize",
        trimmedGitlabEndpoint + "/oauth/token",
        new MemoryDataStoreFactory());
  }

  @Override
  public String getOAuthProvider() {
    return providerName;
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
  public OAuthToken getOrRefreshToken(String userId) throws IOException {
    final OAuthToken token = super.getOrRefreshToken(userId);
    try {
      if (token == null || token.getToken() == null || token.getToken().isEmpty()) {
        return null;
      }
      GitLabUser user = getJson(gitlabUserEndpoint, token.getToken(), GitLabUser.class);
      if (user == null || isNullOrEmpty(user.getId())) {
        return null;
      }
    } catch (OAuthAuthenticationException e) {
      return null;
    }
    return token;
  }

  @Override
  public boolean invalidateToken(String token) {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    format(
                        "%s/oauth/revoke?client_id=%s&client_secret=%s&token=%s",
                        gitlabEndpoint, clientId, clientSecret, token)))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();

    try {
      HttpResponse<InputStream> response =
          client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      return response.statusCode() == 200;
    } catch (IOException | InterruptedException e) {
      return false;
    }
  }

  public String getEndpointUrl() {
    return gitlabEndpoint;
  }
}
