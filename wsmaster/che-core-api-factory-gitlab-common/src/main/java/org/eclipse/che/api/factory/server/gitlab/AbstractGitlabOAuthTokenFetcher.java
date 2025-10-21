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
package org.eclipse.che.api.factory.server.gitlab;

import static java.lang.String.format;
import static org.eclipse.che.commons.lang.StringUtils.trimEnd;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Optional;
import java.util.Set;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenFetcher;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenParams;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.factory.server.scm.exception.UnknownScmProviderException;
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.commons.lang.Pair;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.security.oauth.OAuthAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** GitLab OAuth token retriever. */
public class AbstractGitlabOAuthTokenFetcher implements PersonalAccessTokenFetcher {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractGitlabOAuthTokenFetcher.class);
  public static final Set<String> DEFAULT_TOKEN_SCOPES = ImmutableSet.of("api", "write_repository");

  private final OAuthAPI oAuthAPI;
  private final String serverUrl;
  private final String apiEndpoint;
  private final String providerName;

  public AbstractGitlabOAuthTokenFetcher(
      String serverUrl, String apiEndpoint, OAuthAPI oAuthAPI, String providerName) {
    this.serverUrl = trimEnd(serverUrl, '/');
    this.apiEndpoint = apiEndpoint;
    this.providerName = providerName;
    this.oAuthAPI = oAuthAPI;
  }

  @Override
  public PersonalAccessToken refreshPersonalAccessToken(Subject cheSubject, String scmServerUrl)
      throws ScmUnauthorizedException, ScmCommunicationException, UnknownScmProviderException {
    return fetchOrRefreshPersonalAccessToken(cheSubject, scmServerUrl, true);
  }

  @Override
  public PersonalAccessToken fetchPersonalAccessToken(Subject cheSubject, String scmServerUrl)
      throws ScmUnauthorizedException, ScmCommunicationException, UnknownScmProviderException {
    return fetchOrRefreshPersonalAccessToken(cheSubject, scmServerUrl, false);
  }

  private PersonalAccessToken fetchOrRefreshPersonalAccessToken(
      Subject cheSubject, String scmServerUrl, boolean forceRefreshToken)
      throws ScmUnauthorizedException, ScmCommunicationException, UnknownScmProviderException {
    scmServerUrl = trimEnd(scmServerUrl, '/');
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
              providerName));
    }
    OAuthToken oAuthToken;
    try {
      oAuthToken =
          forceRefreshToken
              ? oAuthAPI.refreshToken(providerName)
              : oAuthAPI.getOrRefreshToken(providerName);
      String tokenName = NameGenerator.generate(OAUTH_2_PREFIX, 5);
      String tokenId = NameGenerator.generate("id-", 5);
      Optional<Pair<Boolean, String>> valid =
          isValid(
              new PersonalAccessTokenParams(
                  scmServerUrl, providerName, tokenName, tokenId, oAuthToken.getToken(), null));
      if (valid.isEmpty()) {
        throw buildScmUnauthorizedException(cheSubject);
      } else if (!valid.get().first) {
        throw new ScmCommunicationException(
            "Current token doesn't have the necessary privileges. Please make sure Che app scopes are correct and containing at least: "
                + DEFAULT_TOKEN_SCOPES);
      }
      return new PersonalAccessToken(
          scmServerUrl,
          providerName,
          cheSubject.getUserId(),
          valid.get().second,
          tokenName,
          tokenId,
          oAuthToken.getToken());
    } catch (UnauthorizedException e) {
      throw buildScmUnauthorizedException(cheSubject);
    } catch (NotFoundException nfe) {
      throw new UnknownScmProviderException(nfe.getMessage(), scmServerUrl);
    } catch (ServerException | ForbiddenException | BadRequestException | ConflictException e) {
      LOG.warn(e.getMessage());
      throw new ScmCommunicationException(e.getMessage(), e);
    }
  }

  private ScmUnauthorizedException buildScmUnauthorizedException(Subject cheSubject) {
    return new ScmUnauthorizedException(
        cheSubject.getUserName() + " is not authorized in " + providerName + " OAuth provider.",
        providerName,
        "2.0",
        getLocalAuthenticateUrl());
  }

  @Override
  public Optional<Boolean> isValid(PersonalAccessToken personalAccessToken) {
    GitlabApiClient gitlabApiClient = getApiClient(personalAccessToken.getScmProviderUrl());
    if (gitlabApiClient == null
        || !gitlabApiClient.isConnected(personalAccessToken.getScmProviderUrl())) {
      if (personalAccessToken.getScmTokenName().equals(providerName)) {
        gitlabApiClient = new GitlabApiClient(personalAccessToken.getScmProviderUrl());
      } else {
        LOG.debug(
            "not a  valid url {} for current fetcher ", personalAccessToken.getScmProviderUrl());
        return Optional.empty();
      }
    }
    if (personalAccessToken.getScmTokenName() != null
        && personalAccessToken.getScmTokenName().startsWith(OAUTH_2_PREFIX)) {
      // validation OAuth token by special API call
      try {
        GitlabOauthTokenInfo info =
            gitlabApiClient.getOAuthTokenInfo(personalAccessToken.getToken());
        return Optional.of(Sets.newHashSet(info.getScope()).containsAll(DEFAULT_TOKEN_SCOPES));
      } catch (ScmItemNotFoundException | ScmCommunicationException | ScmUnauthorizedException e) {
        return Optional.of(Boolean.FALSE);
      }
    } else {
      // validating personal access token from secret. Since PAT API is accessible only in
      // latest GitLab version, we just perform check by accessing something from API.
      try {
        GitlabUser user = gitlabApiClient.getUser(personalAccessToken.getToken());
        if (personalAccessToken.getScmUserName().equals(user.getUsername())) {
          return Optional.of(Boolean.TRUE);
        } else {
          return Optional.of(Boolean.FALSE);
        }
      } catch (ScmItemNotFoundException
          | ScmCommunicationException
          | ScmBadRequestException
          | ScmUnauthorizedException e) {
        return Optional.of(Boolean.FALSE);
      }
    }
  }

  @Override
  public Optional<Pair<Boolean, String>> isValid(PersonalAccessTokenParams params)
      throws ScmCommunicationException {
    GitlabApiClient gitlabApiClient = getApiClient(params.getScmProviderUrl());
    if (gitlabApiClient == null || !gitlabApiClient.isConnected(params.getScmProviderUrl())) {
      if (providerName.equals(params.getScmTokenName())) {
        gitlabApiClient = new GitlabApiClient(params.getScmProviderUrl());
      } else {
        LOG.debug("not a  valid url {} for current fetcher ", params.getScmProviderUrl());
        return Optional.empty();
      }
    }
    try {
      GitlabUser user = gitlabApiClient.getUser(params.getToken());
      if (params.getScmTokenName() != null && params.getScmTokenName().startsWith(OAUTH_2_PREFIX)) {
        // validation OAuth token by special API call
        GitlabOauthTokenInfo info = gitlabApiClient.getOAuthTokenInfo(params.getToken());
        return Optional.of(
            Pair.of(
                Sets.newHashSet(info.getScope()).containsAll(DEFAULT_TOKEN_SCOPES)
                    ? Boolean.TRUE
                    : Boolean.FALSE,
                user.getUsername()));
      }
      // validating personal access token from secret. Since PAT API is accessible only in
      // latest GitLab version, we just perform check by accessing something from API.
      // TODO: add PAT scope validation
      return Optional.of(Pair.of(Boolean.TRUE, user.getUsername()));
    } catch (ScmItemNotFoundException | ScmBadRequestException | ScmUnauthorizedException e) {
      return Optional.empty();
    }
  }

  private String getLocalAuthenticateUrl() {
    return apiEndpoint
        + "/oauth/authenticate?oauth_provider="
        + providerName
        + "&scope="
        + Joiner.on('+').join(DEFAULT_TOKEN_SCOPES)
        + "&request_method=POST&signature_method=rsa";
  }

  private GitlabApiClient getApiClient(String serverUrl) {
    return serverUrl.equals(this.serverUrl) ? new GitlabApiClient(serverUrl) : null;
  }
}
