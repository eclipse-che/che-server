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
package org.eclipse.che.api.factory.server.gitlab;

import static java.lang.String.format;
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
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.lang.StringUtils;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.inject.ConfigurationException;
import org.eclipse.che.security.oauth.OAuthAPI;

/** Gitlab OAuth token retriever. */
public class GitlabUserDataFetcher implements GitUserDataFetcher {
  private final String apiEndpoint;
  private final OAuthAPI oAuthAPI;

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
      OAuthAPI oAuthAPI) {
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
      this.oAuthAPI = oAuthAPI;
    } else {
      this.oAuthAPI = null;
    }
  }

  @Override
  public GitUserData fetchGitUserData() throws ScmUnauthorizedException, ScmCommunicationException {
    if (oAuthAPI == null) {
      throw new ScmCommunicationException(
          format(
              "OAuth 2 is not configured for SCM provider [%s]. For details, refer "
                  + "the documentation in section of SCM providers configuration.",
              OAUTH_PROVIDER_NAME));
    }
    OAuthToken oAuthToken;
    try {
      oAuthToken = oAuthAPI.getToken(OAUTH_PROVIDER_NAME);
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
        | ConflictException e) {
      throw new ScmCommunicationException(e.getMessage(), e);
    }
    GitUserData gitUserData = null;
    for (String gitlabServerEndpoint : this.registeredGitlabEndpoints) {
      try {
        GitlabUser user = new GitlabApiClient(gitlabServerEndpoint).getUser(oAuthToken.getToken());
        gitUserData = new GitUserData(user.getName(), user.getEmail());
        break;
      } catch (ScmItemNotFoundException | ScmBadRequestException e) {
        throw new ScmCommunicationException(e.getMessage(), e);
      }
    }
    if (gitUserData == null) {
      throw new ScmCommunicationException("Failed to retrieve git user data from Gitlab");
    }
    return gitUserData;
  }

  private String getLocalAuthenticateUrl() {
    return apiEndpoint
        + "/oauth/authenticate?oauth_provider="
        + OAUTH_PROVIDER_NAME
        + "&scope="
        + Joiner.on('+').join(DEFAULT_TOKEN_SCOPES)
        + "&request_method=POST&signature_method=rsa";
  }
}
