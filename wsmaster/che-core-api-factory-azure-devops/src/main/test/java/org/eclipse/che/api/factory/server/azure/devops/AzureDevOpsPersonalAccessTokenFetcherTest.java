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

import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.security.oauth.OAuthAPI;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * @author Anatalii Bazko
 */
@Listeners(MockitoTestNGListener.class)
public class AzureDevOpsPersonalAccessTokenFetcherTest {

  private AzureDevOpsPersonalAccessTokenFetcher personalAccessTokenFetcher;

  @BeforeMethod
  protected void start() {
    personalAccessTokenFetcher = new AzureDevOpsPersonalAccessTokenFetcher(
            "localhost",
            "https://dev.azure.com",
            new String[]{},
            mock(AzureDevOpsApiClient.class),
            mock(OAuthAPI.class));
  }

  @Test
  public void fetchPersonalAccessTokenShouldReturnNullIfScmServerUrlIsNotAzureDevOps() throws Exception {
    PersonalAccessToken personalAccessToken = personalAccessTokenFetcher.fetchPersonalAccessToken(mock(Subject.class), "https://eclipse.org");

    assertNull(personalAccessToken);
  }
}
