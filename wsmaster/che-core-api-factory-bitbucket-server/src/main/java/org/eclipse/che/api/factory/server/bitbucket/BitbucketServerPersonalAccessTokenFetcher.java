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
package org.eclipse.che.api.factory.server.bitbucket;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;
import static java.lang.String.valueOf;

import com.google.common.collect.ImmutableSet;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.api.factory.server.bitbucket.server.BitbucketPersonalAccessToken;
import org.eclipse.che.api.factory.server.bitbucket.server.BitbucketServerApiClient;
import org.eclipse.che.api.factory.server.bitbucket.server.BitbucketUser;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenFetcher;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenParams;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.commons.env.EnvironmentContext;
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
  public PersonalAccessToken fetchPersonalAccessToken(Subject cheUser, String scmServerUrl)
      throws ScmUnauthorizedException, ScmCommunicationException {
    if (!bitbucketServerApiClient.isConnected(scmServerUrl)) {
      LOG.debug("not a  valid url {} for current fetcher ", scmServerUrl);
      return null;
    }

    final String tokenName =
        format(TOKEN_NAME_TEMPLATE, cheUser.getUserId(), apiEndpoint.getHost());
    try {
      BitbucketUser user = bitbucketServerApiClient.getUser();
      LOG.debug("Current bitbucket user {} ", user);
      // cleanup existed
      List<BitbucketPersonalAccessToken> existingTokens =
          bitbucketServerApiClient.getPersonalAccessTokens().stream()
              .filter(p -> p.getName().equals(tokenName))
              .collect(Collectors.toList());
      for (BitbucketPersonalAccessToken existedToken : existingTokens) {
        LOG.debug("Deleting existed che token {} {}", existedToken.getId(), existedToken.getName());
        bitbucketServerApiClient.deletePersonalAccessTokens(existedToken.getId());
      }

      BitbucketPersonalAccessToken token =
          bitbucketServerApiClient.createPersonalAccessTokens(tokenName, DEFAULT_TOKEN_SCOPE);
      LOG.debug("Token created = {} for {}", token.getId(), token.getUser());
      return new PersonalAccessToken(
          scmServerUrl,
          EnvironmentContext.getCurrent().getSubject().getUserId(),
          user.getName(),
          user.getSlug(),
          token.getName(),
          valueOf(token.getId()),
          token.getToken());
    } catch (ScmBadRequestException | ScmItemNotFoundException e) {
      throw new ScmCommunicationException(e.getMessage(), e);
    }
  }

  @Override
  public Optional<Boolean> isValid(PersonalAccessToken personalAccessToken)
      throws ScmCommunicationException, ScmUnauthorizedException {
    if (!bitbucketServerApiClient.isConnected(personalAccessToken.getScmProviderUrl())) {
      // If BitBucket oAuth is not configured check the manually added user namespace token.
      HttpBitbucketServerApiClient apiClient =
          new HttpBitbucketServerApiClient(
              personalAccessToken.getScmProviderUrl(),
              new NoopOAuthAuthenticator(),
              oAuthAPI,
              apiEndpoint.toString());
      try {
        apiClient.getUser(personalAccessToken.getToken());
        return Optional.of(Boolean.TRUE);
      } catch (ScmItemNotFoundException
          | ScmUnauthorizedException
          | ScmCommunicationException exception) {
        LOG.debug(
            "not a valid url {} for current fetcher ", personalAccessToken.getScmProviderUrl());
        return Optional.empty();
      }
    }
    try {
      BitbucketPersonalAccessToken bitbucketPersonalAccessToken =
          bitbucketServerApiClient.getPersonalAccessToken(personalAccessToken.getScmTokenId());
      return Optional.of(DEFAULT_TOKEN_SCOPE.equals(bitbucketPersonalAccessToken.getPermissions()));
    } catch (ScmItemNotFoundException e) {
      return Optional.of(Boolean.FALSE);
    }
  }

  @Override
  public Optional<Pair<Boolean, String>> isValid(PersonalAccessTokenParams params) {
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
      // Token is added manually by a user without token id. Validate only by requesting user info.
      if (isNullOrEmpty(params.getScmTokenId())) {
        BitbucketUser user = bitbucketServerApiClient.getUser(params.getToken());
        return Optional.of(Pair.of(Boolean.TRUE, user.getName()));
      }
      // Token is added by OAuth. Token id is available.
      BitbucketPersonalAccessToken bitbucketPersonalAccessToken =
          bitbucketServerApiClient.getPersonalAccessToken(params.getScmTokenId());
      return Optional.of(
          Pair.of(
              DEFAULT_TOKEN_SCOPE.equals(bitbucketPersonalAccessToken.getPermissions())
                  ? Boolean.TRUE
                  : Boolean.FALSE,
              bitbucketPersonalAccessToken.getUser().getName()));
    } catch (ScmItemNotFoundException | ScmUnauthorizedException | ScmCommunicationException e) {
      return Optional.empty();
    }
  }

  @Override
  public String getProviderName(PersonalAccessTokenParams params) {
    return "bitbucket-server";
  }
}
