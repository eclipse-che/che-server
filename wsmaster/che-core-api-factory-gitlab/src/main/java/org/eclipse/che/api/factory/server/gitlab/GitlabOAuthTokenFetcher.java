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
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenFetcher;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.factory.server.scm.exception.UnknownScmProviderException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.commons.lang.StringUtils;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.inject.ConfigurationException;
import org.eclipse.che.security.oauth.OAuthAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** GitLab OAuth token retriever. */
public class GitlabOAuthTokenFetcher implements PersonalAccessTokenFetcher {

  private static final Logger LOG = LoggerFactory.getLogger(GitlabOAuthTokenFetcher.class);
  private static final String OAUTH_PROVIDER_NAME = "gitlab";
  public static final Set<String> DEFAULT_TOKEN_SCOPES =
      ImmutableSet.of("api", "write_repository", "openid");

  private final List<String> registeredGitlabEndpoints;
  private final OAuthAPI oAuthAPI;
  private final String apiEndpoint;

  @Inject
  public GitlabOAuthTokenFetcher(
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
  public PersonalAccessToken fetchPersonalAccessToken(Subject cheSubject, String scmServerUrl)
      throws ScmUnauthorizedException, ScmCommunicationException, UnknownScmProviderException {
    scmServerUrl = StringUtils.trimEnd(scmServerUrl, '/');
    GitlabApiClient gitlabApiClient = getApiClient(scmServerUrl);
    if (gitlabApiClient == null || !gitlabApiClient.isConnected(scmServerUrl)) {
      LOG.debug("not a  valid url {} for current fetcher ", scmServerUrl);
      return null;
    }
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
      GitlabUser user = gitlabApiClient.getUser(oAuthToken.getToken());
      PersonalAccessToken token =
          new PersonalAccessToken(
              scmServerUrl,
              cheSubject.getUserId(),
              user.getUsername(),
              Long.toString(user.getId()),
              NameGenerator.generate(OAUTH_2_PREFIX, 5),
              NameGenerator.generate("id-", 5),
              oAuthToken.getToken());
      Optional<Boolean> valid = isValid(token);
      if (valid.isEmpty() || !valid.get()) {
        throw new ScmCommunicationException(
            "Current token doesn't have the necessary  privileges. Please make sure Che app scopes are correct and containing at least: "
                + DEFAULT_TOKEN_SCOPES.toString());
      }
      return token;
    } catch (UnauthorizedException e) {
      throw new ScmUnauthorizedException(
          cheSubject.getUserName()
              + " is not authorized in "
              + OAUTH_PROVIDER_NAME
              + " OAuth provider.",
          OAUTH_PROVIDER_NAME,
          "2.0",
          getLocalAuthenticateUrl());
    } catch (NotFoundException nfe) {
      throw new UnknownScmProviderException(nfe.getMessage(), scmServerUrl);
    } catch (ServerException
        | ForbiddenException
        | BadRequestException
        | ScmItemNotFoundException
        | ScmBadRequestException
        | ConflictException e) {
      LOG.warn(e.getMessage());
      throw new ScmCommunicationException(e.getMessage(), e);
    }
  }

  @Override
  public Optional<Boolean> isValid(PersonalAccessToken personalAccessToken) {
    GitlabApiClient gitlabApiClient = getApiClient(personalAccessToken.getScmProviderUrl());
    if (gitlabApiClient == null
        || !gitlabApiClient.isConnected(personalAccessToken.getScmProviderUrl())) {
      LOG.debug(
          "not a  valid url {} for current fetcher ", personalAccessToken.getScmProviderUrl());
      return Optional.empty();
    }
    if (personalAccessToken.getScmTokenName() != null
        && personalAccessToken.getScmTokenName().startsWith(OAUTH_2_PREFIX)) {
      // validation OAuth token by special API call
      try {
        GitlabOauthTokenInfo info = gitlabApiClient.getTokenInfo(personalAccessToken.getToken());
        return Optional.of(Sets.newHashSet(info.getScope()).containsAll(DEFAULT_TOKEN_SCOPES));
      } catch (ScmItemNotFoundException | ScmCommunicationException e) {
        return Optional.of(Boolean.FALSE);
      }
    } else {
      // validating personal access token from secret. Since PAT API is accessible only in
      // latest GitLab version, we just perform check by accessing something from API.
      try {
        GitlabUser user = gitlabApiClient.getUser(personalAccessToken.getToken());
        if (personalAccessToken.getScmUserId().equals(Long.toString(user.getId()))) {
          return Optional.of(Boolean.TRUE);
        } else {
          return Optional.of(Boolean.FALSE);
        }
      } catch (ScmItemNotFoundException | ScmCommunicationException | ScmBadRequestException e) {
        return Optional.of(Boolean.FALSE);
      }
    }
  }

  private String getLocalAuthenticateUrl() {
    return apiEndpoint
        + "/oauth/authenticate?oauth_provider="
        + OAUTH_PROVIDER_NAME
        + "&scope="
        + Joiner.on('+').join(DEFAULT_TOKEN_SCOPES)
        + "&request_method=POST&signature_method=rsa";
  }

  private GitlabApiClient getApiClient(String scmServerUrl) {
    return registeredGitlabEndpoints.contains(scmServerUrl)
        ? new GitlabApiClient(scmServerUrl)
        : null;
  }
}
