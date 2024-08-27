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
package org.eclipse.che.api.factory.server.bitbucket;

import com.google.common.collect.Sets;
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

/** Bitbucket OAuth token retriever. */
public class BitbucketPersonalAccessTokenFetcher implements PersonalAccessTokenFetcher {

  private static final Logger LOG =
      LoggerFactory.getLogger(BitbucketPersonalAccessTokenFetcher.class);
  private final String apiEndpoint;
  private final OAuthAPI oAuthAPI;

  /** Bitbucket API client. */
  private final BitbucketApiClient bitbucketApiClient;

  /** Name of this OAuth provider as found in OAuthAPI. */
  private static final String OAUTH_PROVIDER_NAME = "bitbucket";

  /** OAuth scope required to make integration with Bitbucket work. */
  public static final String DEFAULT_REPOSITORY_WRITE_TOKEN_SCOPE = "repository:write";

  public static final String DEFAULT_PULLREQUEST_WRITE_TOKEN_SCOPE = "pullrequest:write";

  public static final String DEFAULT_ACCOUNT_READ_TOKEN_SCOPE = "account";

  public static final String DEFAULT_ACCOUNT_WRITE_TOKEN_SCOPE = "account:write";

  @Inject
  public BitbucketPersonalAccessTokenFetcher(
      @Named("che.api") String apiEndpoint, OAuthAPI oAuthAPI) {
    this(apiEndpoint, oAuthAPI, new BitbucketApiClient());
  }

  /**
   * Constructor used for testing only.
   *
   * @param apiEndpoint
   * @param oAuthAPI
   * @param bitbucketApiClient
   */
  BitbucketPersonalAccessTokenFetcher(
      String apiEndpoint, OAuthAPI oAuthAPI, BitbucketApiClient bitbucketApiClient) {
    this.apiEndpoint = apiEndpoint;
    this.oAuthAPI = oAuthAPI;
    this.bitbucketApiClient = bitbucketApiClient;
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
    OAuthToken oAuthToken;

    if (bitbucketApiClient == null || !bitbucketApiClient.isConnected(scmServerUrl)) {
      LOG.debug("not a  valid url {} for current fetcher ", scmServerUrl);
      return null;
    }
    try {
      oAuthToken =
          forceRefreshToken
              ? oAuthAPI.refreshToken(OAUTH_PROVIDER_NAME)
              : oAuthAPI.getOrRefreshToken(OAUTH_PROVIDER_NAME);
      String tokenName = NameGenerator.generate(OAUTH_PROVIDER_NAME, 5);
      String tokenId = NameGenerator.generate("id-", 5);
      Optional<Pair<Boolean, String>> valid =
          isValid(
              new PersonalAccessTokenParams(
                  scmServerUrl,
                  OAUTH_PROVIDER_NAME,
                  tokenName,
                  tokenId,
                  oAuthToken.getToken(),
                  null));
      if (valid.isEmpty()) {
        throw buildScmUnauthorizedException(cheSubject);
      } else if (!valid.get().first) {
        throw new ScmCommunicationException(
            "Current token doesn't have the necessary privileges. Please make sure Che app scopes are correct and containing at least: "
                + DEFAULT_REPOSITORY_WRITE_TOKEN_SCOPE
                + " and "
                + DEFAULT_ACCOUNT_READ_TOKEN_SCOPE);
      }
      return new PersonalAccessToken(
          scmServerUrl,
          OAUTH_PROVIDER_NAME,
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
      LOG.error(e.getMessage());
      throw new ScmCommunicationException(e.getMessage(), e);
    }
  }

  private ScmUnauthorizedException buildScmUnauthorizedException(Subject cheSubject) {
    return new ScmUnauthorizedException(
        cheSubject.getUserName()
            + " is not authorized in "
            + OAUTH_PROVIDER_NAME
            + " OAuth provider.",
        OAUTH_PROVIDER_NAME,
        "2.0",
        getLocalAuthenticateUrl());
  }

  @Override
  public Optional<Boolean> isValid(PersonalAccessToken personalAccessToken) {
    if (!bitbucketApiClient.isConnected(personalAccessToken.getScmProviderUrl())) {
      LOG.debug("not a valid url {} for current fetcher ", personalAccessToken.getScmProviderUrl());
      return Optional.empty();
    }

    try {
      String[] scopes = bitbucketApiClient.getTokenScopes(personalAccessToken.getToken()).second;
      return Optional.of(isValidScope(Sets.newHashSet(scopes)));
    } catch (ScmItemNotFoundException
        | ScmCommunicationException
        | ScmBadRequestException
        | ScmUnauthorizedException e) {
      return Optional.of(Boolean.FALSE);
    }
  }

  @Override
  public Optional<Pair<Boolean, String>> isValid(PersonalAccessTokenParams params)
      throws ScmCommunicationException {
    if (!bitbucketApiClient.isConnected(params.getScmProviderUrl())) {
      LOG.debug("not a valid url {} for current fetcher ", params.getScmProviderUrl());
      return Optional.empty();
    }

    try {
      Pair<String, String[]> pair = bitbucketApiClient.getTokenScopes(params.getToken());
      return Optional.of(
          Pair.of(
              isValidScope(Sets.newHashSet(pair.second)) ? Boolean.TRUE : Boolean.FALSE,
              pair.first));
    } catch (ScmItemNotFoundException | ScmBadRequestException | ScmUnauthorizedException e) {
      return Optional.empty();
    }
  }

  private String getLocalAuthenticateUrl() {
    return apiEndpoint
        + "/oauth/authenticate?oauth_provider="
        + OAUTH_PROVIDER_NAME
        + "&scope=repository&request_method=POST&signature_method=rsa";
  }

  /**
   * Checks if the given scopes are valid for Bitbucket. Note: that pullrequest:write is a wider
   * scope than repository:write, and account:write is a wider scope than account.
   */
  private boolean isValidScope(Set<String> scopes) {
    return (scopes.contains(DEFAULT_REPOSITORY_WRITE_TOKEN_SCOPE)
            || scopes.contains(DEFAULT_PULLREQUEST_WRITE_TOKEN_SCOPE))
        && (scopes.contains(DEFAULT_ACCOUNT_READ_TOKEN_SCOPE)
            || scopes.contains(DEFAULT_ACCOUNT_WRITE_TOKEN_SCOPE));
  }
}
