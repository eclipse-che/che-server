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
package org.eclipse.che.api.factory.server.github;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
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
import org.mockito.Mockito;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class GithubAuthorizingFileContentProviderTest {

  private PersonalAccessTokenManager personalAccessTokenManager;

  @BeforeMethod
  void start() {
    personalAccessTokenManager = mock(PersonalAccessTokenManager.class);
  }

  @Test
  public void shouldExpandRelativePaths() throws Exception {
    URLFetcher urlFetcher = mock(URLFetcher.class);

    GithubUrl githubUrl =
        new GithubUrl("github")
            .withUsername("eclipse")
            .withRepository("che")
            .withBranch("main")
            .withServerUrl("https://github.com")
            .withLatestCommit("d74923ebf968454cf13251f17df69dcd87d3b932");

    FileContentProvider fileContentProvider =
        new GithubAuthorizingFileContentProvider(githubUrl, urlFetcher, personalAccessTokenManager);

    when(personalAccessTokenManager.getAndStore(anyString()))
        .thenReturn(new PersonalAccessToken("foo", "che", "my-token"));

    fileContentProvider.fetchContent("devfile.yaml");

    verify(urlFetcher)
        .fetch(
            eq(
                "https://raw.githubusercontent.com/eclipse/che/d74923ebf968454cf13251f17df69dcd87d3b932/devfile.yaml"),
            eq("token my-token"));
  }

  @Test
  public void shouldPreserveAbsolutePaths() throws Exception {
    String raw_url = "https://raw.githubusercontent.com/foo/bar/branch-name/devfile.yaml";

    GithubUrl githubUrl =
        new GithubUrl("github")
            .withUsername("eclipse")
            .withRepository("che")
            .withServerUrl("https://github.com")
            .withBranch("main")
            .withLatestCommit("9ac2f42ed62944d164f189afd57f14a2793a7e4b");

    URLFetcher urlFetcher = mock(URLFetcher.class);
    FileContentProvider fileContentProvider =
        new GithubAuthorizingFileContentProvider(githubUrl, urlFetcher, personalAccessTokenManager);

    when(personalAccessTokenManager.getAndStore(anyString()))
        .thenReturn(new PersonalAccessToken(raw_url, "che", "my-token"));

    fileContentProvider.fetchContent(raw_url);
    verify(urlFetcher).fetch(eq(raw_url), eq("token my-token"));
  }

  @Test(expectedExceptions = FileNotFoundException.class)
  public void shouldThrowNotFoundForPublicRepos() throws Exception {
    String url = "https://raw.githubusercontent.com/foo/bar/branch-name/devfile.yaml";

    GithubUrl githubUrl =
        new GithubUrl("github")
            .withUsername("eclipse")
            .withRepository("che")
            .withServerUrl("https://github.com");

    URLFetcher urlFetcher = Mockito.mock(URLFetcher.class);
    FileContentProvider fileContentProvider =
        new GithubAuthorizingFileContentProvider(githubUrl, urlFetcher, personalAccessTokenManager);

    when(personalAccessTokenManager.getAndStore(anyString()))
        .thenThrow(UnknownScmProviderException.class);

    when(urlFetcher.fetch(eq(url))).thenThrow(FileNotFoundException.class);

    fileContentProvider.fetchContent(url);
  }

  @Test(expectedExceptions = DevfileException.class)
  public void shouldThrowDevfileException() throws Exception {
    String url = "https://raw.githubusercontent.com/foo/bar/branch-name/devfile.yaml";
    GithubUrl githubUrl =
        new GithubUrl("github")
            .withUsername("eclipse")
            .withRepository("che")
            .withServerUrl("https://github.com");

    URLFetcher urlFetcher = Mockito.mock(URLFetcher.class);
    FileContentProvider fileContentProvider =
        new GithubAuthorizingFileContentProvider(githubUrl, urlFetcher, personalAccessTokenManager);

    when(personalAccessTokenManager.getAndStore(anyString()))
        .thenThrow(UnknownScmProviderException.class);
    when(urlFetcher.fetch(eq(url))).thenThrow(FileNotFoundException.class);
    when(urlFetcher.fetch(eq("https://github.com/eclipse/che"))).thenThrow(IOException.class);

    fileContentProvider.fetchContent(url);
  }

  @Test
  public void shouldNotAskGitHubAPIForDifferentDomain() throws Exception {
    String raw_url = "https://ghserver.com/foo/bar/branch-name/devfile.yaml";

    URLFetcher urlFetcher = Mockito.mock(URLFetcher.class);
    GithubUrl githubUrl =
        new GithubUrl("github")
            .withUsername("eclipse")
            .withRepository("che")
            .withServerUrl("https://github.com");
    FileContentProvider fileContentProvider =
        new GithubAuthorizingFileContentProvider(githubUrl, urlFetcher, personalAccessTokenManager);
    var personalAccessToken = new PersonalAccessToken(raw_url, "che", "my-token");
    when(personalAccessTokenManager.getAndStore(anyString())).thenReturn(personalAccessToken);

    fileContentProvider.fetchContent(raw_url);

    verify(urlFetcher).fetch(eq(raw_url), eq("token my-token"));
  }
}
