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
package org.eclipse.che.api.factory.server.azure.devops;

import static org.eclipse.che.api.factory.server.azure.devops.AzureDevOps.getAuthenticateUrlPath;
import static org.eclipse.che.commons.lang.StringUtils.trimEnd;

import java.util.Arrays;
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

/**
 * Azure DevOps Service OAuth2 token fetcher.
 *
 * @author Anatolii Bazko
 */
public class AzureDevOpsPersonalAccessTokenFetcher implements PersonalAccessTokenFetcher {

  private static final Logger LOG =
      LoggerFactory.getLogger(AzureDevOpsPersonalAccessTokenFetcher.class);
  private static final String OAUTH_PROVIDER_NAME = "azure-devops";
  private final String cheApiEndpoint;
  private final String azureDevOpsScmApiEndpoint;
  private final OAuthAPI oAuthAPI;
  private final String[] scopes;

  private final AzureDevOpsApiClient azureDevOpsApiClient;

  @Inject
  public AzureDevOpsPersonalAccessTokenFetcher(
      @Named("che.api") String cheApiEndpoint,
      @Named("che.integration.azure.devops.scm.api_endpoint") String azureDevOpsScmApiEndpoint,
      @Named("che.integration.azure.devops.application_scopes") String[] scopes,
      AzureDevOpsApiClient azureDevOpsApiClient,
      OAuthAPI oAuthAPI) {
    this.cheApiEndpoint = cheApiEndpoint;
    this.azureDevOpsScmApiEndpoint = trimEnd(azureDevOpsScmApiEndpoint, '/');
    this.oAuthAPI = oAuthAPI;
    this.scopes = scopes;
    this.azureDevOpsApiClient = azureDevOpsApiClient;
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

    if (!isValidScmServerUrl(scmServerUrl)) {
      LOG.debug("not a  valid url {} for current fetcher ", scmServerUrl);
      return null;
    }

    try {
      oAuthToken =
          forceRefreshToken
              ? oAuthAPI.refreshToken(AzureDevOps.PROVIDER_NAME)
              : oAuthAPI.getOrRefreshToken(AzureDevOps.PROVIDER_NAME);
      String tokenName = NameGenerator.generate(OAUTH_2_PREFIX, 5);
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
                + Arrays.toString(scopes));
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
            + AzureDevOps.PROVIDER_NAME
            + " OAuth provider.",
        AzureDevOps.PROVIDER_NAME,
        "2.0",
        getLocalAuthenticateUrl());
  }

  @Override
  public Optional<Boolean> isValid(PersonalAccessToken personalAccessToken) {
    if (!isValidScmServerUrl(personalAccessToken.getScmProviderUrl())) {
      LOG.debug("not a valid url {} for current fetcher ", personalAccessToken.getScmProviderUrl());
      return Optional.empty();
    }

    try {
      AzureDevOpsUser user;
      if (personalAccessToken.getScmTokenName() != null
          && personalAccessToken.getScmTokenName().startsWith(OAUTH_2_PREFIX)) {
        user = azureDevOpsApiClient.getUserWithOAuthToken(personalAccessToken.getToken());
      } else {
        user =
            azureDevOpsApiClient.getUserWithPAT(
                personalAccessToken.getToken(), personalAccessToken.getScmOrganization());
      }
      return Optional.of(personalAccessToken.getScmUserName().equals(user.getEmailAddress()));
    } catch (ScmItemNotFoundException | ScmCommunicationException | ScmBadRequestException e) {
      return Optional.of(Boolean.FALSE);
    }
  }

  @Override
  public Optional<Pair<Boolean, String>> isValid(PersonalAccessTokenParams params)
      throws ScmCommunicationException {
    if (!isValidScmServerUrl(params.getScmProviderUrl())) {
      LOG.debug("not a valid url {} for current fetcher ", params.getScmProviderUrl());
      return Optional.empty();
    }

    try {
      AzureDevOpsUser user;
      if (params.getScmTokenName() != null && params.getScmTokenName().startsWith(OAUTH_2_PREFIX)) {
        user = azureDevOpsApiClient.getUserWithOAuthToken(params.getToken());
      } else {
        user = azureDevOpsApiClient.getUserWithPAT(params.getToken(), params.getOrganization());
      }
      return Optional.of(Pair.of(Boolean.TRUE, user.getEmailAddress()));
    } catch (ScmItemNotFoundException | ScmBadRequestException e) {
      return Optional.empty();
    }
  }

  private String getLocalAuthenticateUrl() {
    return cheApiEndpoint + getAuthenticateUrlPath(scopes);
  }

  private Boolean isValidScmServerUrl(String scmServerUrl) {
    return azureDevOpsScmApiEndpoint.equals(trimEnd(scmServerUrl, '/'));
  }
}
