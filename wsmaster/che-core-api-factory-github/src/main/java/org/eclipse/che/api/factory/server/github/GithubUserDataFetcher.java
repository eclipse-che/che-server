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
package org.eclipse.che.api.factory.server.github;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.factory.server.scm.AbstractGitUserDataFetcher;
import org.eclipse.che.api.factory.server.scm.GitUserData;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.security.oauth.OAuthAPI;

/** GitHub user data retriever. */
public class GithubUserDataFetcher extends AbstractGitUserDataFetcher {
  private final String apiEndpoint;
  /** GitHub API client. */
  private final GithubApiClient githubApiClient;

  /** Name of this OAuth provider as found in OAuthAPI. */
  private static final String OAUTH_PROVIDER_NAME = "github";

  /** Collection of OAuth scopes required to make integration with GitHub work. */
  public static final Set<String> DEFAULT_TOKEN_SCOPES =
      ImmutableSet.of("repo", "user:email", "read:user");

  @Inject
  public GithubUserDataFetcher(
      @Named("che.api") String apiEndpoint,
      @Nullable @Named("che.integration.github.oauth_endpoint") String oauthEndpoint,
      OAuthAPI oAuthTokenFetcher,
      PersonalAccessTokenManager personalAccessTokenManager) {
    this(
        apiEndpoint,
        oAuthTokenFetcher,
        personalAccessTokenManager,
        new GithubApiClient(oauthEndpoint));
  }

  /** Constructor used for testing only. */
  public GithubUserDataFetcher(
      String apiEndpoint,
      OAuthAPI oAuthTokenFetcher,
      PersonalAccessTokenManager personalAccessTokenManager,
      GithubApiClient githubApiClient) {
    super(OAUTH_PROVIDER_NAME, personalAccessTokenManager, oAuthTokenFetcher);
    this.githubApiClient = githubApiClient;
    this.apiEndpoint = apiEndpoint;
  }

  @Override
  protected GitUserData fetchGitUserDataWithOAuthToken(OAuthToken oAuthToken)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException {
    GithubUser user = githubApiClient.getUser(oAuthToken.getToken());
    return new GitUserData(user.getName(), user.getEmail());
  }

  @Override
  protected GitUserData fetchGitUserDataWithPersonalAccessToken(
      PersonalAccessToken personalAccessToken)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException {
    GithubUser user = githubApiClient.getUser(personalAccessToken.getToken());
    return new GitUserData(user.getName(), user.getEmail());
  }

  protected String getLocalAuthenticateUrl() {
    return apiEndpoint
        + "/oauth/authenticate?oauth_provider="
        + OAUTH_PROVIDER_NAME
        + "&scope="
        + Joiner.on(',').join(DEFAULT_TOKEN_SCOPES)
        + "&request_method=POST&signature_method=rsa";
  }
}
