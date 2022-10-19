/*
 * Copyright (c) 2012-2022 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.factory.server.gitlab;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.che.api.factory.server.scm.GitCredentialManager;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.workspace.server.devfile.FileContentProvider;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class GitlabAuthorizingFileContentProviderTest {

  @Mock private GitCredentialManager gitCredentialsManager;
  @Mock private PersonalAccessTokenManager personalAccessTokenManager;

  @Test
  public void shouldExpandRelativePaths() throws Exception {
    URLFetcher urlFetcher = Mockito.mock(URLFetcher.class);
    GitlabUrl gitlabUrl =
        new GitlabUrl()
            .withHostName("https://gitlab.net")
            .withUsername("eclipse")
            .withProject("che");
    FileContentProvider fileContentProvider =
        new GitlabAuthorizingFileContentProvider(
            gitlabUrl, urlFetcher, gitCredentialsManager, personalAccessTokenManager);
    var personalAccessToken = new PersonalAccessToken("foo", "che", "my-token");
    when(personalAccessTokenManager.fetchAndSave(any(), anyString()))
        .thenReturn(personalAccessToken);
    fileContentProvider.fetchContent("devfile.yaml");
    verify(urlFetcher)
        .fetch(
            eq(
                "https://gitlab.net/api/v4/projects/eclipse%2Fche/repository/files/devfile.yaml/raw"),
            eq("Bearer my-token"));
  }

  @Test
  public void shouldPreserveAbsolutePaths() throws Exception {
    URLFetcher urlFetcher = Mockito.mock(URLFetcher.class);
    GitlabUrl gitlabUrl =
        new GitlabUrl().withHostName("gitlab.net").withUsername("eclipse").withProject("che");
    FileContentProvider fileContentProvider =
        new GitlabAuthorizingFileContentProvider(
            gitlabUrl, urlFetcher, gitCredentialsManager, personalAccessTokenManager);
    String url =
        "https://gitlab.net/api/v4/projects/eclipse%2Fche/repository/files/devfile.yaml/raw";
    var personalAccessToken = new PersonalAccessToken(url, "che", "my-token");
    when(personalAccessTokenManager.fetchAndSave(any(), anyString()))
        .thenReturn(personalAccessToken);

    fileContentProvider.fetchContent(url);
    verify(urlFetcher).fetch(eq(url), eq("Bearer my-token"));
  }
}
