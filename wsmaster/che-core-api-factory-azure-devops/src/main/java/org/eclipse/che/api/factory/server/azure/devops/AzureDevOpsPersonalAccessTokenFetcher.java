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

/**
 * Azure DevOps Service OAuth2 token fetcher.
 *
 * @author Anatolii Bazko
 */
public class AzureDevOpsPersonalAccessTokenFetcher implements PersonalAccessTokenFetcher {

  private static final Logger LOG =
      LoggerFactory.getLogger(AzureDevOpsPersonalAccessTokenFetcher.class);
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
  public PersonalAccessToken fetchPersonalAccessToken(Subject cheSubject, String scmServerUrl)
      throws ScmUnauthorizedException, ScmCommunicationException, UnknownScmProviderException {
    OAuthToken oAuthToken;

    if (!isValidScmServerUrl(scmServerUrl)) {
      LOG.debug("not a  valid url {} for current fetcher ", scmServerUrl);
      return null;
    }

    try {
      oAuthToken = oAuthAPI.getToken(AzureDevOps.PROVIDER_NAME);
      // Find the user associated to the OAuth token by querying the Azure DevOps API.
      AzureDevOpsUser user = azureDevOpsApiClient.getUserWithOAuthToken(oAuthToken.getToken());
      PersonalAccessToken token =
          new PersonalAccessToken(
              scmServerUrl,
              cheSubject.getUserId(),
              user.getEmailAddress(),
              NameGenerator.generate(OAUTH_2_PREFIX, 5),
              NameGenerator.generate("id-", 5),
              oAuthToken.getToken());
      Optional<Boolean> valid = isValid(token);
      if (valid.isEmpty()) {
        throw new ScmCommunicationException(
            "Unable to verify if current token is a valid Azure DevOps token.  Token's scm-url needs to be '"
                + azureDevOpsScmApiEndpoint
                + "' and was '"
                + token.getScmProviderUrl()
                + "'");
      } else if (!valid.get()) {
        throw new ScmCommunicationException(
            "Current token doesn't have the necessary privileges. Please make sure Che app scopes are correct and containing at least: "
                + Arrays.toString(scopes));
      }
      return token;
    } catch (UnauthorizedException e) {
      throw new ScmUnauthorizedException(
          cheSubject.getUserName()
              + " is not authorized in "
              + AzureDevOps.PROVIDER_NAME
              + " OAuth provider.",
          AzureDevOps.PROVIDER_NAME,
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

  private String getLocalAuthenticateUrl() {
    return cheApiEndpoint + getAuthenticateUrlPath(scopes);
  }

  private Boolean isValidScmServerUrl(String scmServerUrl) {
    return azureDevOpsScmApiEndpoint.equals(trimEnd(scmServerUrl, '/'));
  }
}
