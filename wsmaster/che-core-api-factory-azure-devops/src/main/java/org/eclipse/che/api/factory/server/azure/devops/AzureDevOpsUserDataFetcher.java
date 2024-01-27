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
import org.eclipse.che.api.factory.server.scm.AbstractGitUserDataFetcher;
import org.eclipse.che.api.factory.server.scm.GitUserData;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;

/**
 * Azure DevOps user data fetcher.
 *
 * @author Anatolii Bazko
 */
public class AzureDevOpsUserDataFetcher extends AbstractGitUserDataFetcher {
  private final String cheApiEndpoint;
  private final String[] scopes;
  private final AzureDevOpsApiClient azureDevOpsApiClient;

  @Inject
  public AzureDevOpsUserDataFetcher(
      PersonalAccessTokenManager personalAccessTokenManager,
      AzureDevOpsApiClient azureDevOpsApiClient,
      @Named("che.api") String cheApiEndpoint,
      @Named("che.integration.azure.devops.application_scopes") String[] scopes) {
    super(AzureDevOps.PROVIDER_NAME, personalAccessTokenManager);
    this.scopes = scopes;
    this.cheApiEndpoint = cheApiEndpoint;
    this.azureDevOpsApiClient = azureDevOpsApiClient;
  }

  @Override
  protected GitUserData fetchGitUserDataWithPersonalAccessToken(
      PersonalAccessToken personalAccessToken)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException {
    AzureDevOpsUser user =
        azureDevOpsApiClient.getUserWithPAT(
            personalAccessToken.getToken(), personalAccessToken.getScmOrganization());
    return new GitUserData(user.getDisplayName(), user.getEmailAddress());
  }

  protected String getLocalAuthenticateUrl() {
    return cheApiEndpoint + getAuthenticateUrlPath(scopes);
  }
}
