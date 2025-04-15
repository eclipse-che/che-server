/*
 * Copyright (c) 2012-2025 Red Hat, Inc.
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

import com.google.common.collect.ImmutableSet;
import java.net.URL;
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
import org.eclipse.che.api.factory.server.bitbucket.server.BitbucketPersonalAccessToken;
import org.eclipse.che.api.factory.server.bitbucket.server.BitbucketServerApiClient;
import org.eclipse.che.api.factory.server.bitbucket.server.BitbucketUser;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenFetcher;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenParams;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.factory.server.scm.exception.UnknownScmProviderException;
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.commons.lang.Pair;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.security.oauth.OAuthAPI;
import org.eclipse.che.security.oauth1.NoopOAuthAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bitbucket implementation for {@link PersonalAccessTokenFetcher}. Right now returns {@code null}
 * for all possible SCM URL-s (which is valid value) but later will be extended to fully featured
 * class.
 */
public class BitbucketServerPersonalAccessTokenFetcher implements PersonalAccessTokenFetcher {

  private static final Logger LOG =
      LoggerFactory.getLogger(BitbucketServerPersonalAccessTokenFetcher.class);

  private static final String OAUTH_PROVIDER_NAME = "bitbucket-server";

  private static final String TOKEN_NAME_TEMPLATE = "che-token-<%s>-<%s>";
  public static final Set<String> DEFAULT_TOKEN_SCOPE =
      ImmutableSet.of("PROJECT_WRITE", "REPO_WRITE");
  private final BitbucketServerApiClient bitbucketServerApiClient;
  private final URL apiEndpoint;
  private final OAuthAPI oAuthAPI;

  @Inject
  public BitbucketServerPersonalAccessTokenFetcher(
      BitbucketServerApiClient bitbucketServerApiClient,
      @Named("che.api") URL apiEndpoint,
      OAuthAPI oAuthAPI) {
    this.bitbucketServerApiClient = bitbucketServerApiClient;
    this.apiEndpoint = apiEndpoint;
    this.oAuthAPI = oAuthAPI;
  }

  @Override
  public PersonalAccessToken refreshPersonalAccessToken(Subject cheUser, String scmServerUrl)
      throws ScmUnauthorizedException, ScmCommunicationException, UnknownScmProviderException {
    return fetchOrRefreshPersonalAccessToken(cheUser, scmServerUrl, true);
  }

  @Override
  public PersonalAccessToken fetchPersonalAccessToken(Subject cheSubject, String scmServerUrl)
      throws ScmUnauthorizedException, ScmCommunicationException, UnknownScmProviderException {
    return fetchOrRefreshPersonalAccessToken(cheSubject, scmServerUrl, false);
  }

  private PersonalAccessToken fetchOrRefreshPersonalAccessToken(
      Subject cheSubject, String scmServerUrl, boolean forceRefreshToken)
      throws ScmCommunicationException, ScmUnauthorizedException, UnknownScmProviderException {
    OAuthToken oAuthToken;
    if (bitbucketServerApiClient == null || !bitbucketServerApiClient.isConnected(scmServerUrl)) {
      LOG.debug("not a  valid url {} for current fetcher ", scmServerUrl);
      return null;
    }
    try {
      BitbucketUser user = bitbucketServerApiClient.getUser();
      oAuthToken =
          forceRefreshToken
              ? oAuthAPI.refreshToken(OAUTH_PROVIDER_NAME)
              : oAuthAPI.getOrRefreshToken(OAUTH_PROVIDER_NAME);
      String tokenName = NameGenerator.generate(OAUTH_2_PREFIX, 5);
      String tokenId = NameGenerator.generate("id-", 5);
      return new PersonalAccessToken(
          scmServerUrl,
          OAUTH_PROVIDER_NAME,
          cheSubject.getUserId(),
          user.getSlug(),
          tokenName,
          tokenId,
          oAuthToken.getToken());
    } catch (NotFoundException nfe) {
      throw new UnknownScmProviderException(nfe.getMessage(), scmServerUrl);
    } catch (ServerException
        | ForbiddenException
        | BadRequestException
        | ConflictException
        | ScmItemNotFoundException
        | UnauthorizedException e) {
      LOG.error(e.getMessage());
      throw new ScmCommunicationException(e.getMessage(), e);
    }
  }

  @Override
  public Optional<Boolean> isValid(PersonalAccessToken accessToken)
      throws ScmCommunicationException, ScmUnauthorizedException {
    if (!bitbucketServerApiClient.isConnected(accessToken.getScmProviderUrl())) {
      // If BitBucket oAuth is not configured check the manually added user namespace token.
      HttpBitbucketServerApiClient apiClient =
          new HttpBitbucketServerApiClient(
              accessToken.getScmProviderUrl(),
              new NoopOAuthAuthenticator(),
              oAuthAPI,
              apiEndpoint.toString());
      try {
        apiClient.getUser(accessToken.getToken());
        return Optional.of(Boolean.TRUE);
      } catch (ScmItemNotFoundException
          | ScmUnauthorizedException
          | ScmCommunicationException exception) {
        LOG.debug("not a valid url {} for current fetcher ", accessToken.getScmProviderUrl());
        return Optional.empty();
      }
    }
    try {
      BitbucketPersonalAccessToken bitbucketPersonalAccessToken =
          bitbucketServerApiClient.getPersonalAccessToken(
              accessToken.getScmTokenId(),
              // Pass oauth token to fetch personal access token
              // TODO: rename the PersonalAccessToken interface to more generic name, so both OAuth
              // and personal access token implementations would be suitable.
              accessToken.getToken());
      return Optional.of(DEFAULT_TOKEN_SCOPE.equals(bitbucketPersonalAccessToken.getPermissions()));
    } catch (ScmItemNotFoundException e) {
      return Optional.of(Boolean.FALSE);
    }
  }

  @Override
  public Optional<Pair<Boolean, String>> isValid(PersonalAccessTokenParams params)
      throws ScmCommunicationException {
    if (!bitbucketServerApiClient.isConnected(params.getScmProviderUrl())) {
      // If BitBucket oAuth is not configured check the manually added user namespace token.
      HttpBitbucketServerApiClient apiClient =
          new HttpBitbucketServerApiClient(
              params.getScmProviderUrl(),
              new NoopOAuthAuthenticator(),
              oAuthAPI,
              apiEndpoint.toString());
      try {
        BitbucketUser user = apiClient.getUser(params.getToken());
        return Optional.of(Pair.of(Boolean.TRUE, user.getName()));
      } catch (ScmItemNotFoundException
          | ScmUnauthorizedException
          | ScmCommunicationException exception) {
        LOG.debug("not a valid url {} for current fetcher ", params.getScmProviderUrl());
        return Optional.empty();
      }
    }
    try {
      BitbucketUser user = bitbucketServerApiClient.getUser(params.getToken());
      return Optional.of(Pair.of(Boolean.TRUE, user.getName()));
    } catch (ScmItemNotFoundException | ScmUnauthorizedException e) {
      return Optional.empty();
    }
  }
}
