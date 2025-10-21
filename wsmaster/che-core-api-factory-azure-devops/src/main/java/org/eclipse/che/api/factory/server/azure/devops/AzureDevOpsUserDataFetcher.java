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
package org.eclipse.che.api.factory.server.azure.devops;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.eclipse.che.api.factory.server.azure.devops.AzureDevOps.SAAS_ENDPOINT;
import static org.eclipse.che.api.factory.server.azure.devops.AzureDevOps.getAuthenticateUrlPath;

import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.api.factory.server.scm.AbstractGitUserDataFetcher;
import org.eclipse.che.api.factory.server.scm.GitUserData;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;

/**
 * Azure DevOps user data fetcher.
 *
 * @author Anatolii Bazko
 */
public class AzureDevOpsUserDataFetcher extends AbstractGitUserDataFetcher {
  private final String cheApiEndpoint;
  private final String[] scopes;
  private final AzureDevOpsApiClient azureDevOpsApiClient;
  private static final String NO_USERNAME_AND_EMAIL_ERROR_MESSAGE =
      "User name and/or email is not found in the azure devops profile.";

  @Inject
  public AzureDevOpsUserDataFetcher(
      PersonalAccessTokenManager personalAccessTokenManager,
      AzureDevOpsApiClient azureDevOpsApiClient,
      @Named("che.api") String cheApiEndpoint,
      @Named("che.integration.azure.devops.scm.api_endpoint") String azureDevOpsScmApiEndpoint,
      @Named("che.integration.azure.devops.application_scopes") String[] scopes) {
    super(AzureDevOps.PROVIDER_NAME, azureDevOpsScmApiEndpoint, personalAccessTokenManager);
    this.scopes = scopes;
    this.cheApiEndpoint = cheApiEndpoint;
    this.azureDevOpsApiClient = azureDevOpsApiClient;
  }

  @Override
  protected GitUserData fetchGitUserDataWithOAuthToken(String token)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException,
          ScmUnauthorizedException {
    AzureDevOpsUser user = azureDevOpsApiClient.getUserWithOAuthToken(token);
    return new GitUserData(user.getDisplayName(), user.getEmailAddress());
  }

  @Override
  protected GitUserData fetchGitUserDataWithPersonalAccessToken(
      PersonalAccessToken personalAccessToken)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException,
          ScmUnauthorizedException {
    if (SAAS_ENDPOINT.equals(personalAccessToken.getScmProviderUrl())) {
      AzureDevOpsUser user =
          azureDevOpsApiClient.getUserWithPAT(
              personalAccessToken.getToken(), personalAccessToken.getScmOrganization());
      if (isNullOrEmpty(user.getDisplayName()) || isNullOrEmpty(user.getEmailAddress())) {
        throw new ScmItemNotFoundException(NO_USERNAME_AND_EMAIL_ERROR_MESSAGE);
      } else {
        return new GitUserData(user.getDisplayName(), user.getEmailAddress());
      }
    } else {
      AzureDevOpsServerApiClient apiClient =
          new AzureDevOpsServerApiClient(
              personalAccessToken.getScmProviderUrl(), personalAccessToken.getScmOrganization());
      AzureDevOpsServerUserProfile user = apiClient.getUser(personalAccessToken.getToken());
      String defaultMailAddress = user.getDefaultMailAddress();
      String identityMailAddress = user.getIdentity().getMailAddress();
      String preferredEmail = user.getUserPreferences().getPreferredEmail();
      String email =
          isNullOrEmpty(defaultMailAddress)
              ? (isNullOrEmpty(identityMailAddress) ? preferredEmail : identityMailAddress)
              : defaultMailAddress;
      String name = user.getIdentity().getAccountName();
      if (isNullOrEmpty(name) || isNullOrEmpty(email)) {
        throw new ScmItemNotFoundException(NO_USERNAME_AND_EMAIL_ERROR_MESSAGE);
      } else {
        return new GitUserData(name, email);
      }
    }
  }

  protected String getLocalAuthenticateUrl() {
    return cheApiEndpoint + getAuthenticateUrlPath(scopes);
  }
}
