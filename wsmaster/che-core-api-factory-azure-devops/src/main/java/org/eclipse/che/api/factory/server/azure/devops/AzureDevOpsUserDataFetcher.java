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
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.security.oauth.OAuthAPI;

/**
 * Azure DevOps user data fetcher.
 *
 * @author Anatolii Bazko
 */
public class AzureDevOpsUserDataFetcher implements GitUserDataFetcher {
  private final String cheApiEndpoint;
  private final String[] scopes;
  private final OAuthAPI oAuthAPI;

  private final AzureDevOpsApiClient azureDevOpsApiClient;

  @Inject
  public AzureDevOpsUserDataFetcher(
      OAuthAPI oAuthAPI,
      AzureDevOpsApiClient azureDevOpsApiClient,
      @Named("che.api") String cheApiEndpoint,
      @Named("che.integration.azure.devops.application_scopes") String[] scopes) {
    this.scopes = scopes;
    this.cheApiEndpoint = cheApiEndpoint;
    this.oAuthAPI = oAuthAPI;
    this.azureDevOpsApiClient = azureDevOpsApiClient;
  }

  @Override
  public GitUserData fetchGitUserData() throws ScmUnauthorizedException, ScmCommunicationException {
    OAuthToken oAuthToken;
    try {
      oAuthToken = oAuthAPI.getToken(AzureDevOps.PROVIDER_NAME);
      AzureDevOpsUser user = azureDevOpsApiClient.getUserWithOAuthToken(oAuthToken.getToken());
      return new GitUserData(user.getDisplayName(), user.getEmailAddress());
    } catch (UnauthorizedException e) {
      Subject cheSubject = EnvironmentContext.getCurrent().getSubject();
      throw new ScmUnauthorizedException(
          cheSubject.getUserName()
              + " is not authorized in "
              + AzureDevOps.PROVIDER_NAME
              + " OAuth provider.",
          AzureDevOps.PROVIDER_NAME,
          "2.0",
          getLocalAuthenticateUrl());
    } catch (NotFoundException
        | ServerException
        | ForbiddenException
        | BadRequestException
        | ScmItemNotFoundException
        | ScmBadRequestException
        | ConflictException e) {
      throw new ScmCommunicationException(e.getMessage(), e);
    }
  }

  private String getLocalAuthenticateUrl() {
    return cheApiEndpoint + getAuthenticateUrlPath(scopes);
  }
}
