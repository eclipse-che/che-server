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

import com.google.common.collect.Sets;
import java.util.Optional;
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
import org.eclipse.che.commons.lang.NameGenerator;
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
  public static final String DEFAULT_TOKEN_SCOPE = "repository:write";

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
  public PersonalAccessToken fetchPersonalAccessToken(Subject cheSubject, String scmServerUrl)
      throws ScmUnauthorizedException, ScmCommunicationException, UnknownScmProviderException {
    OAuthToken oAuthToken;

    if (bitbucketApiClient == null || !bitbucketApiClient.isConnected(scmServerUrl)) {
      LOG.debug("not a  valid url {} for current fetcher ", scmServerUrl);
      return null;
    }
    try {
      oAuthToken = oAuthAPI.getToken(OAUTH_PROVIDER_NAME);
      // Find the user associated to the OAuth token by querying the Bitbucket API.
      BitbucketUser user = bitbucketApiClient.getUser(oAuthToken.getToken());
      PersonalAccessToken token =
          new PersonalAccessToken(
              scmServerUrl,
              cheSubject.getUserId(),
              user.getName(),
              NameGenerator.generate(OAUTH_PROVIDER_NAME, 5),
              NameGenerator.generate("id-", 5),
              oAuthToken.getToken());
      Optional<Boolean> valid = isValid(token);
      if (valid.isEmpty()) {
        throw new ScmCommunicationException(
            "Unable to verify if current token is a valid Bitbucket token.  Token's scm-url needs to be '"
                + BitbucketApiClient.BITBUCKET_SERVER
                + "' and was '"
                + token.getScmProviderUrl()
                + "'");
      } else if (!valid.get()) {
        throw new ScmCommunicationException(
            "Current token doesn't have the necessary privileges. Please make sure Che app scopes are correct and containing at least: "
                + DEFAULT_TOKEN_SCOPE);
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
      LOG.error(e.getMessage());
      throw new ScmCommunicationException(e.getMessage(), e);
    }
  }

  @Override
  public Optional<Boolean> isValid(PersonalAccessToken personalAccessToken) {
    if (!bitbucketApiClient.isConnected(personalAccessToken.getScmProviderUrl())) {
      LOG.debug("not a valid url {} for current fetcher ", personalAccessToken.getScmProviderUrl());
      return Optional.empty();
    }

    try {
      String[] scopes = bitbucketApiClient.getTokenScopes(personalAccessToken.getToken());
      return Optional.of(Sets.newHashSet(scopes).contains(DEFAULT_TOKEN_SCOPE));
    } catch (ScmItemNotFoundException | ScmCommunicationException | ScmBadRequestException e) {
      return Optional.of(Boolean.FALSE);
    }
  }

  private String getLocalAuthenticateUrl() {
    return apiEndpoint
        + "/oauth/authenticate?oauth_provider="
        + OAUTH_PROVIDER_NAME
        + "&scope=repository&request_method=POST&signature_method=rsa";
  }
}
