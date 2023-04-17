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
package org.eclipse.che.api.factory.server.gitlab;

import static java.util.stream.Collectors.toList;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.factory.server.scm.*;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.lang.StringUtils;
import org.eclipse.che.inject.ConfigurationException;

/** Gitlab OAuth token retriever. */
public class GitlabUserDataFetcher extends AbstractGitUserDataFetcher {
  private final String apiEndpoint;

  /** Name of this OAuth provider as found in OAuthAPI. */
  private static final String OAUTH_PROVIDER_NAME = "gitlab";

  private final List<String> registeredGitlabEndpoints;

  public static final Set<String> DEFAULT_TOKEN_SCOPES =
      ImmutableSet.of("api", "write_repository", "openid");

  @Inject
  public GitlabUserDataFetcher(
      @Nullable @Named("che.integration.gitlab.server_endpoints") String gitlabEndpoints,
      @Nullable @Named("che.integration.gitlab.oauth_endpoint") String oauthEndpoint,
      @Named("che.api") String apiEndpoint,
      PersonalAccessTokenManager personalAccessTokenManager,
      OAuthTokenFetcher oAuthTokenFetcher) {
    super(OAUTH_PROVIDER_NAME, personalAccessTokenManager, oAuthTokenFetcher);
    this.apiEndpoint = apiEndpoint;
    if (gitlabEndpoints != null) {
      this.registeredGitlabEndpoints =
          Splitter.on(",")
              .splitToStream(gitlabEndpoints)
              .map(e -> StringUtils.trimEnd(e, '/'))
              .collect(toList());
    } else {
      this.registeredGitlabEndpoints = Collections.emptyList();
    }
    if (oauthEndpoint != null) {
      if (!registeredGitlabEndpoints.contains(StringUtils.trimEnd(oauthEndpoint, '/'))) {
        throw new ConfigurationException(
            "GitLab OAuth integration endpoint must be present in registered GitLab endpoints list.");
      }
    }
  }

  @Override
  protected GitUserData fetchGitUserDataWithOAuthToken(OAuthToken oAuthToken)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException {
    for (String gitlabServerEndpoint : this.registeredGitlabEndpoints) {
      GitlabUser user = new GitlabApiClient(gitlabServerEndpoint).getUser(oAuthToken.getToken());
      return new GitUserData(user.getName(), user.getEmail());
    }
    throw new ScmCommunicationException("Failed to retrieve git user data from Gitlab");
  }

  @Override
  protected GitUserData fetchGitUserDataWithPersonalAccessToken(
      PersonalAccessToken personalAccessToken)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException {
    GitlabUser user =
        new GitlabApiClient(personalAccessToken.getScmProviderUrl())
            .getUser(personalAccessToken.getToken());
    return new GitUserData(user.getName(), user.getEmail());
  }

  protected String getLocalAuthenticateUrl() {
    return apiEndpoint
        + "/oauth/authenticate?oauth_provider="
        + OAUTH_PROVIDER_NAME
        + "&scope="
        + Joiner.on('+').join(DEFAULT_TOKEN_SCOPES)
        + "&request_method=POST&signature_method=rsa";
  }
}
