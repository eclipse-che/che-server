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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.security.oauth.OAuthAPI;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/** @author Anatalii Bazko */
@Listeners(MockitoTestNGListener.class)
public class AzureDevOpsPersonalAccessTokenFetcherTest {

  @Mock private AzureDevOpsApiClient azureDevOpsApiClient;
  @Mock private OAuthAPI oAuthAPI;
  @Mock private OAuthToken oAuthToken;
  @Mock private AzureDevOpsUser azureDevOpsUser;
  private AzureDevOpsPersonalAccessTokenFetcher personalAccessTokenFetcher;

  @BeforeMethod
  protected void start() {
    personalAccessTokenFetcher =
        new AzureDevOpsPersonalAccessTokenFetcher(
            "localhost", "https://dev.azure.com", new String[] {}, azureDevOpsApiClient, oAuthAPI);
  }

  @Test
  public void fetchPersonalAccessTokenShouldReturnNullIfScmServerUrlIsNotAzureDevOps()
      throws Exception {
    PersonalAccessToken personalAccessToken =
        personalAccessTokenFetcher.fetchPersonalAccessToken(
            mock(Subject.class), "https://eclipse.org");

    assertNull(personalAccessToken);
  }

  @Test
  public void fetchPersonalAccessTokenShouldReturnToken() throws Exception {
    when(oAuthAPI.getToken(AzureDevOps.PROVIDER_NAME)).thenReturn(oAuthToken);
    when(azureDevOpsApiClient.getUserWithOAuthToken(any())).thenReturn(azureDevOpsUser);
    when(azureDevOpsUser.getEmailAddress()).thenReturn("user-email");

    PersonalAccessToken personalAccessToken =
        personalAccessTokenFetcher.fetchPersonalAccessToken(
            mock(Subject.class), "https://dev.azure.com/");

    assertNotNull(personalAccessToken);
  }
}
