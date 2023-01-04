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
package org.eclipse.che.api.factory.server.bitbucket;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class BitbucketServerAuthorizingFileContentProviderTest {

  public static final String TEST_HOSTNAME = "https://foo.bar";
  @Mock private URLFetcher urlFetcher;
  @Mock private PersonalAccessTokenManager personalAccessTokenManager;

  @Test
  public void shouldFetchContentWithTokenIfPresent() throws Exception {
    BitbucketServerUrl url = new BitbucketServerUrl().withHostName(TEST_HOSTNAME);
    BitbucketServerAuthorizingFileContentProvider fileContentProvider =
        new BitbucketServerAuthorizingFileContentProvider(
            url, urlFetcher, personalAccessTokenManager);

    PersonalAccessToken token = new PersonalAccessToken(TEST_HOSTNAME, "user1", "token");
    when(personalAccessTokenManager.getAndStore(anyString())).thenReturn(token);

    String fileURL = "https://foo.bar/scm/repo/.devfile";

    // when
    fileContentProvider.fetchContent(fileURL);

    // then
    verify(urlFetcher).fetch(eq(fileURL), eq("Bearer token"));
  }

  @Test
  public void shouldFetchTokenIfNotYetPresent() throws Exception {
    BitbucketServerUrl url = new BitbucketServerUrl().withHostName(TEST_HOSTNAME);
    BitbucketServerAuthorizingFileContentProvider fileContentProvider =
        new BitbucketServerAuthorizingFileContentProvider(
            url, urlFetcher, personalAccessTokenManager);

    PersonalAccessToken token = new PersonalAccessToken(TEST_HOSTNAME, "user1", "token");
    when(personalAccessTokenManager.getAndStore(eq(TEST_HOSTNAME))).thenReturn(token);

    String fileURL = "https://foo.bar/scm/repo/.devfile";

    // when
    fileContentProvider.fetchContent(fileURL);

    // then
    verify(personalAccessTokenManager).getAndStore(eq(TEST_HOSTNAME));
    verify(urlFetcher).fetch(eq(fileURL), eq("Bearer token"));
  }

  @Test(dataProvider = "relativePathsProvider")
  public void shouldResolveRelativePaths(String relative, String expected, String branch)
      throws Exception {
    BitbucketServerUrl url =
        new BitbucketServerUrl()
            .withHostName(TEST_HOSTNAME)
            .withProject("proj")
            .withRepository("repo")
            .withDevfileFilenames(Collections.singletonList(".devfile"));
    if (branch != null) {
      url.withBranch(branch);
    }
    BitbucketServerAuthorizingFileContentProvider fileContentProvider =
        new BitbucketServerAuthorizingFileContentProvider(
            url, urlFetcher, personalAccessTokenManager);
    PersonalAccessToken token = new PersonalAccessToken(TEST_HOSTNAME, "user1", "token");
    when(personalAccessTokenManager.getAndStore(anyString())).thenReturn(token);

    // when
    fileContentProvider.fetchContent(relative);

    // then
    verify(urlFetcher).fetch(eq(expected), eq("Bearer token"));
  }

  @DataProvider
  public static Object[][] relativePathsProvider() {
    return new Object[][] {
      {"./file.txt", "https://foo.bar/rest/api/1.0/projects/proj/repos/repo/raw/file.txt", null},
      {"/file.txt", "https://foo.bar/rest/api/1.0/projects/proj/repos/repo/raw/file.txt", null},
      {"file.txt", "https://foo.bar/rest/api/1.0/projects/proj/repos/repo/raw/file.txt", null},
      {
        "foo/file.txt",
        "https://foo.bar/rest/api/1.0/projects/proj/repos/repo/raw/foo/file.txt",
        null
      },
      {
        "file.txt",
        "https://foo.bar/rest/api/1.0/projects/proj/repos/repo/raw/file.txt?at=foo",
        "foo"
      }
    };
  }
}
