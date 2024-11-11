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
package org.eclipse.che.api.factory.server.gitlab;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.UnknownScmProviderException;
import org.eclipse.che.api.workspace.server.devfile.FileContentProvider;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class GitlabAuthorizingFileContentProviderTest {
  @Mock private PersonalAccessTokenManager personalAccessTokenManager;

  @Test
  public void shouldExpandRelativePaths() throws Exception {
    URLFetcher urlFetcher = Mockito.mock(URLFetcher.class);
    GitlabUrl gitlabUrl = new GitlabUrl().withHostName("gitlab.net").withSubGroups("eclipse/che");
    FileContentProvider fileContentProvider =
        new GitlabAuthorizingFileContentProvider(gitlabUrl, urlFetcher, personalAccessTokenManager);
    var personalAccessToken = new PersonalAccessToken("foo", "provider", "che", "my-token");
    when(personalAccessTokenManager.getAndStore(anyString())).thenReturn(personalAccessToken);
    fileContentProvider.fetchContent("devfile.yaml");
    verify(urlFetcher)
        .fetch(
            eq(
                "https://gitlab.net/api/v4/projects/eclipse%2Fche/repository/files/devfile.yaml/raw?ref=HEAD"),
            eq("Bearer my-token"));
  }

  @Test
  public void shouldPreserveAbsolutePaths() throws Exception {
    URLFetcher urlFetcher = Mockito.mock(URLFetcher.class);
    GitlabUrl gitlabUrl = new GitlabUrl().withHostName("gitlab.net").withSubGroups("eclipse/che");
    FileContentProvider fileContentProvider =
        new GitlabAuthorizingFileContentProvider(gitlabUrl, urlFetcher, personalAccessTokenManager);
    String url =
        "https://gitlab.net/api/v4/projects/eclipse%2Fche/repository/files/devfile.yaml/raw";
    var personalAccessToken = new PersonalAccessToken(url, "provider", "che", "my-token");
    when(personalAccessTokenManager.getAndStore(anyString())).thenReturn(personalAccessToken);

    fileContentProvider.fetchContent(url);
    verify(urlFetcher).fetch(eq(url), eq("Bearer my-token"));
  }

  @Test(expectedExceptions = FileNotFoundException.class)
  public void shouldThrowFileNotFoundException() throws Exception {
    // given
    URLFetcher urlFetcher = Mockito.mock(URLFetcher.class);
    when(urlFetcher.fetch(
            eq(
                "https://gitlab.com/api/v4/projects/eclipse%2Fche/repository/files/devfile.yaml/raw?ref=HEAD")))
        .thenThrow(new FileNotFoundException());
    when(urlFetcher.fetch(eq("https://gitlab.com/eclipse/che"))).thenReturn("content");
    when(personalAccessTokenManager.getAndStore(anyString()))
        .thenThrow(new UnknownScmProviderException("", ""));
    GitlabUrl gitlabUrl = new GitlabUrl().withHostName("gitlab.com").withSubGroups("eclipse/che");
    FileContentProvider fileContentProvider =
        new GitlabAuthorizingFileContentProvider(gitlabUrl, urlFetcher, personalAccessTokenManager);

    // when
    fileContentProvider.fetchContent("devfile.yaml");
  }
}
