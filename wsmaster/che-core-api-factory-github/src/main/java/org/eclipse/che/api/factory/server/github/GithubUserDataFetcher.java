/*
 * Copyright (c) 2012-2022 Red Hat, Inc.
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
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.api.factory.server.scm.GitUserData;
import org.eclipse.che.api.factory.server.scm.GitUserDataFetcher;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.security.oauth.OAuthAPI;

/** GitHub user data retriever. */
public class GithubUserDataFetcher implements GitUserDataFetcher {
  private final String apiEndpoint;
  private final OAuthAPI oAuthAPI;

  /** GitHub API client. */
  private final GithubApiClient githubApiClient;

  /** Name of this OAuth provider as found in OAuthAPI. */
  private static final String OAUTH_PROVIDER_NAME = "github";

  /** Collection of OAuth scopes required to make integration with GitHub work. */
  public static final Set<String> DEFAULT_TOKEN_SCOPES = ImmutableSet.of("repo");

  @Inject
  public GithubUserDataFetcher(@Named("che.api") String apiEndpoint, OAuthAPI oAuthAPI) {
    this(apiEndpoint, oAuthAPI, new GithubApiClient());
  }

  /** Constructor used for testing only. */
  public GithubUserDataFetcher(
      String apiEndpoint, OAuthAPI oAuthAPI, GithubApiClient githubApiClient) {
    this.apiEndpoint = apiEndpoint;
    this.oAuthAPI = oAuthAPI;
    this.githubApiClient = githubApiClient;
  }

  @Override
  public GitUserData fetchGitUserData() throws ScmUnauthorizedException, ScmCommunicationException {
    OAuthToken oAuthToken;
    try {
      oAuthToken = oAuthAPI.getToken(OAUTH_PROVIDER_NAME);
      // Find the user associated to the OAuth token by querying the GitHub API.
      GithubUser user = githubApiClient.getUser(oAuthToken.getToken());
      return new GitUserData(user.getName(), user.getEmail());
    } catch (UnauthorizedException e) {
      Subject cheSubject = EnvironmentContext.getCurrent().getSubject();
      throw new ScmUnauthorizedException(
          cheSubject.getUserName()
              + " is not authorized in "
              + OAUTH_PROVIDER_NAME
              + " OAuth provider.",
          OAUTH_PROVIDER_NAME,
          "2.0",
          getLocalAuthenticateUrl());
    } catch (NotFoundException
        | ServerException
        | ForbiddenException
        | BadRequestException
        | ScmItemNotFoundException
        | ScmBadRequestException
        | ConflictException e) {
      throw new ScmCommunicationException(e.getMessage(), e);
    }
  }

  private String getLocalAuthenticateUrl() {
    return apiEndpoint
        + "/oauth/authenticate?oauth_provider="
        + OAUTH_PROVIDER_NAME
        + "&scope="
        + Joiner.on(',').join(DEFAULT_TOKEN_SCOPES)
        + "&request_method=POST&signature_method=rsa";
  }
}
