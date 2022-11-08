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
package org.eclipse.che.api.factory.server.github;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import java.io.IOException;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.UnknownScmProviderException;
import org.eclipse.che.api.workspace.server.devfile.FileContentProvider;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.api.workspace.server.devfile.exception.DevfileException;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class GithubAuthorizingFileContentProviderTest {

  @Mock private PersonalAccessTokenManager personalAccessTokenManager;
  @Mock private URLFetcher urlFetcher;
  private static final String URL = "https://raw.githubusercontent.com/foo/bar/devfile.yaml";
  GithubUrl githubUrl;
  FileContentProvider fileContentProvider;

  @BeforeMethod
  public void setup() {
    githubUrl = new GithubUrl().withUsername("eclipse").withRepository("che");
    fileContentProvider =
        new GithubAuthorizingFileContentProvider(githubUrl, urlFetcher, personalAccessTokenManager);
  }

  @Test
  public void shouldExpandRelativePaths() throws Exception {
    var personalAccessToken = new PersonalAccessToken("foo", "che", "my-token");
    when(personalAccessTokenManager.getAndStore(anyString())).thenReturn(personalAccessToken);
    fileContentProvider.fetchContent("devfile.yaml");
    verify(urlFetcher)
        .fetch(
            eq("https://raw.githubusercontent.com/eclipse/che/HEAD/devfile.yaml"),
            eq("token my-token"));
  }

  @Test
  public void shouldPreserveAbsolutePaths() throws Exception {
    String url = "https://raw.githubusercontent.com/foo/bar/devfile.yaml";
    var personalAccessToken = new PersonalAccessToken(url, "che", "my-token");
    when(personalAccessTokenManager.getAndStore(anyString())).thenReturn(personalAccessToken);
    fileContentProvider.fetchContent(url);
    verify(urlFetcher).fetch(eq(url), eq("token my-token"));
  }

  @Test(expectedExceptions = FileNotFoundException.class)
  public void shouldThrowNotFoundForPublicRepos() throws Exception {
    when(personalAccessTokenManager.getAndStore(anyString()))
        .thenThrow(UnknownScmProviderException.class);
    when(urlFetcher.fetch(eq(URL))).thenThrow(FileNotFoundException.class);
    when(urlFetcher.fetch(eq("https://github.com/eclipse/che"))).thenReturn("OK");
    fileContentProvider.fetchContent(URL);
  }

  @Test(expectedExceptions = DevfileException.class)
  public void shouldThrowDevfileException() throws Exception {
    // given
    when(personalAccessTokenManager.getAndStore(anyString()))
        .thenThrow(UnknownScmProviderException.class);
    when(urlFetcher.fetch(eq(URL))).thenThrow(FileNotFoundException.class);
    when(urlFetcher.fetch(eq("https://github.com/eclipse/che"))).thenThrow(IOException.class);
    // when
    fileContentProvider.fetchContent(URL);
  }
}
