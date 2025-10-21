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
package org.eclipse.che.api.factory.server.github;

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.eclipse.che.api.factory.server.scm.AbstractGitUserDataFetcher;
import org.eclipse.che.api.factory.server.scm.GitUserData;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;

/** GitHub user data retriever. */
public abstract class AbstractGithubUserDataFetcher extends AbstractGitUserDataFetcher {
  private final String apiEndpoint;
  /** GitHub API client. */
  private final GithubApiClient githubApiClient;

  /** Name of this OAuth provider as found in OAuthAPI. */
  private final String providerName;
  /** Collection of OAuth scopes required to make integration with GitHub work. */
  public static final Set<String> DEFAULT_TOKEN_SCOPES =
      ImmutableSet.of("repo", "user:email", "read:user");

  private static final String NO_USERNAME_AND_EMAIL_ERROR_MESSAGE =
      "User name and/or email is not found in the GitHub profile.";

  /** Constructor used for testing only. */
  public AbstractGithubUserDataFetcher(
      String apiEndpoint,
      PersonalAccessTokenManager personalAccessTokenManager,
      GithubApiClient githubApiClient,
      String providerName) {
    super(providerName, githubApiClient.getServerUrl(), personalAccessTokenManager);
    this.providerName = providerName;
    this.githubApiClient = githubApiClient;
    this.apiEndpoint = apiEndpoint;
  }

  @Override
  protected GitUserData fetchGitUserDataWithOAuthToken(String token)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException,
          ScmUnauthorizedException {
    GithubUser user = githubApiClient.getUser(token);
    if (isNullOrEmpty(user.getName()) || isNullOrEmpty(user.getEmail())) {
      throw new ScmItemNotFoundException(NO_USERNAME_AND_EMAIL_ERROR_MESSAGE);
    } else {
      return new GitUserData(user.getName(), user.getEmail());
    }
  }

  @Override
  protected GitUserData fetchGitUserDataWithPersonalAccessToken(
      PersonalAccessToken personalAccessToken)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException,
          ScmUnauthorizedException {
    GithubApiClient apiClient =
        githubApiClient.isConnected(personalAccessToken.getScmProviderUrl())
            ? githubApiClient
            : new GithubApiClient(personalAccessToken.getScmProviderUrl());
    GithubUser user = apiClient.getUser(personalAccessToken.getToken());
    if (isNullOrEmpty(user.getName()) || isNullOrEmpty(user.getEmail())) {
      throw new ScmItemNotFoundException(NO_USERNAME_AND_EMAIL_ERROR_MESSAGE);
    } else {
      return new GitUserData(user.getName(), user.getEmail());
    }
  }

  protected String getLocalAuthenticateUrl() {
    return apiEndpoint
        + "/oauth/authenticate?oauth_provider="
        + providerName
        + "&scope="
        + Joiner.on(',').join(DEFAULT_TOKEN_SCOPES)
        + "&request_method=POST&signature_method=rsa";
  }
}
