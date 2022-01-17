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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import org.eclipse.che.api.factory.server.scm.GitCredentialManager;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.workspace.server.devfile.FileContentProvider;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class GithubAuthorizingFileContentProviderTest {

  @Mock private GitCredentialManager gitCredentialManager;

  @Mock private PersonalAccessTokenManager personalAccessTokenManager;

  @Test
  public void shouldExpandRelativePaths() throws Exception {
    URLFetcher urlFetcher = Mockito.mock(URLFetcher.class);
    GithubUrl githubUrl = new GithubUrl().withUsername("eclipse").withRepository("che");
    FileContentProvider fileContentProvider =
        new GithubAuthorizingFileContentProvider(
            githubUrl, urlFetcher, gitCredentialManager, personalAccessTokenManager);
    fileContentProvider.fetchContent("devfile.yaml");
    verify(urlFetcher).fetch(eq("https://raw.githubusercontent.com/eclipse/che/HEAD/devfile.yaml"));
  }

  @Test
  public void shouldPreserveAbsolutePaths() throws Exception {
    URLFetcher urlFetcher = Mockito.mock(URLFetcher.class);
    GithubUrl githubUrl = new GithubUrl().withUsername("eclipse").withRepository("che");
    FileContentProvider fileContentProvider =
        new GithubAuthorizingFileContentProvider(
            githubUrl, urlFetcher, gitCredentialManager, personalAccessTokenManager);
    String url = "https://raw.githubusercontent.com/foo/bar/devfile.yaml";
    fileContentProvider.fetchContent(url);
    verify(urlFetcher).fetch(eq(url));
  }

  @Test(expectedExceptions = FileNotFoundException.class)
  public void shouldThrowNotFoundForPublicRepos() throws Exception {
    URLFetcher urlFetcher = Mockito.mock(URLFetcher.class);
    String url = "https://raw.githubusercontent.com/foo/bar/devfile.yaml";
    when(urlFetcher.fetch(eq(url))).thenThrow(FileNotFoundException.class);
    when(urlFetcher.fetch(eq("https://api.github.com/repos/eclipse/che"))).thenReturn("OK");
    GithubUrl githubUrl = new GithubUrl().withUsername("eclipse").withRepository("che");
    FileContentProvider fileContentProvider =
        new GithubAuthorizingFileContentProvider(
            githubUrl, urlFetcher, gitCredentialManager, personalAccessTokenManager);
    fileContentProvider.fetchContent(url);
  }
}
