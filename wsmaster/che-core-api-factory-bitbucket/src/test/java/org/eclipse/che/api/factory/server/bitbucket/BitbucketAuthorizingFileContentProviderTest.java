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
package org.eclipse.che.api.factory.server.bitbucket;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import org.eclipse.che.api.factory.server.scm.GitCredentialManager;
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
public class BitbucketAuthorizingFileContentProviderTest {

  @Mock private GitCredentialManager gitCredentialManager;

  @Mock private PersonalAccessTokenManager personalAccessTokenManager;

  @Test
  public void shouldExpandRelativePaths() throws Exception {
    URLFetcher urlFetcher = Mockito.mock(URLFetcher.class);
    BitbucketUrl bitbucketUrl = new BitbucketUrl().withWorkspaceId("eclipse").withRepository("che");
    FileContentProvider fileContentProvider =
        new BitbucketAuthorizingFileContentProvider(
            bitbucketUrl, urlFetcher, gitCredentialManager, personalAccessTokenManager);
    var personalAccessToken = new PersonalAccessToken("foo", "che", "my-token");
    when(personalAccessTokenManager.fetchAndSave(any(), anyString()))
        .thenReturn(personalAccessToken);
    fileContentProvider.fetchContent("devfile.yaml");
    verify(urlFetcher)
        .fetch(
            eq("https://bitbucket.org/eclipse/che/raw/HEAD/devfile.yaml"), eq("Bearer my-token"));
  }

  @Test
  public void shouldPreserveAbsolutePaths() throws Exception {
    URLFetcher urlFetcher = Mockito.mock(URLFetcher.class);
    BitbucketUrl bitbucketUrl = new BitbucketUrl().withUsername("eclipse").withRepository("che");
    FileContentProvider fileContentProvider =
        new BitbucketAuthorizingFileContentProvider(
            bitbucketUrl, urlFetcher, gitCredentialManager, personalAccessTokenManager);
    String url = "https://api.bitbucket.org/2.0/repositories/foo/bar/devfile.yaml";
    var personalAccessToken = new PersonalAccessToken(url, "che", "my-token");
    when(personalAccessTokenManager.fetchAndSave(any(), anyString()))
        .thenReturn(personalAccessToken);
    fileContentProvider.fetchContent(url);
    verify(urlFetcher).fetch(eq(url), eq("Bearer my-token"));
  }

  @Test(expectedExceptions = FileNotFoundException.class)
  public void shouldThrowNotFoundForPublicRepos() throws Exception {
    URLFetcher urlFetcher = Mockito.mock(URLFetcher.class);
    String url = "https://bitbucket.org/foo/bar/raw/HEAD/devfile.yaml";
    when(personalAccessTokenManager.fetchAndSave(any(), anyString()))
        .thenThrow(UnknownScmProviderException.class);
    when(urlFetcher.fetch(eq(url))).thenThrow(FileNotFoundException.class);
    when(urlFetcher.fetch(eq("https://bitbucket.org/eclipse/che"))).thenReturn("OK");
    BitbucketUrl bitbucketUrl =
        new BitbucketUrl().withUsername("eclipse").withWorkspaceId("eclipse").withRepository("che");
    FileContentProvider fileContentProvider =
        new BitbucketAuthorizingFileContentProvider(
            bitbucketUrl, urlFetcher, gitCredentialManager, personalAccessTokenManager);
    fileContentProvider.fetchContent(url);
  }
}
