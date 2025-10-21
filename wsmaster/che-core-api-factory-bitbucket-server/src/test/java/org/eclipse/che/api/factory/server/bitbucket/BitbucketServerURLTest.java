/*
 * Copyright (c) 2012-2025 Red Hat, Inc.
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

import static java.lang.String.format;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.util.Arrays;
import java.util.Iterator;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl;
import org.eclipse.che.security.oauth.OAuthAPI;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class BitbucketServerURLTest {
  private BitbucketServerURLParser bitbucketServerURLParser;
  @Mock private DevfileFilenamesProvider devfileFilenamesProvider;

  @BeforeMethod
  protected void init() {
    when(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .thenReturn(Arrays.asList("devfile.yaml", "foo.bar"));
    bitbucketServerURLParser =
        new BitbucketServerURLParser(
            "https://bitbucket.net",
            devfileFilenamesProvider,
            mock(OAuthAPI.class),
            mock(PersonalAccessTokenManager.class));
  }

  @Test(dataProvider = "urlsProvider")
  public void checkDevfileLocation(String repoUrl, String fileUrl) {
    lenient()
        .when(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .thenReturn(Arrays.asList("devfile.yaml", "foo.bar"));

    BitbucketServerUrl gitlabUrl = bitbucketServerURLParser.parse(repoUrl, null);
    assertEquals(gitlabUrl.devfileFileLocations().size(), 2);
    Iterator<RemoteFactoryUrl.DevfileLocation> iterator =
        gitlabUrl.devfileFileLocations().iterator();
    assertEquals(iterator.next().location(), format(fileUrl, "devfile.yaml"));
    assertEquals(iterator.next().location(), format(fileUrl, "foo.bar"));
  }

  @DataProvider
  public static Object[][] urlsProvider() {
    return new Object[][] {
      {
        "https://bitbucket.net/scm/~user/repo.git",
        "https://bitbucket.net/rest/api/1.0/users/user/repos/repo/raw/%s"
      },
      {
        "https://bitbucket.net/users/user/repos/repo/browse?at=branch",
        "https://bitbucket.net/rest/api/1.0/users/user/repos/repo/raw/%s?at=branch"
      },
      {
        "https://bitbucket.net/users/user/repos/repo",
        "https://bitbucket.net/rest/api/1.0/users/user/repos/repo/raw/%s"
      },
      {
        "https://bitbucket.net/scm/project/repo.git",
        "https://bitbucket.net/rest/api/1.0/projects/project/repos/repo/raw/%s"
      },
      {
        "https://bitbucket.net/projects/project/repos/repo/browse?at=branch",
        "https://bitbucket.net/rest/api/1.0/projects/project/repos/repo/raw/%s?at=branch"
      },
      {
        "ssh://git@bitbucket.net:12345/project/repo.git",
        "https://bitbucket.net/rest/api/1.0/projects/project/repos/repo/raw/%s"
      },
      {
        "ssh://git@bitbucket.net:12345/~user/repo.git",
        "https://bitbucket.net/rest/api/1.0/users/user/repos/repo/raw/%s"
      }
    };
  }

  @Test(dataProvider = "repoProvider")
  public void checkRepositoryLocation(String rawUrl, String repoUrl) {
    BitbucketServerUrl bitbucketServerUrl = bitbucketServerURLParser.parse(rawUrl, null);
    assertEquals(bitbucketServerUrl.repositoryLocation(), repoUrl);
  }

  @Test(dataProvider = "urlsProvider")
  public void shouldReturnProviderUrl(String repoUrl, String ignored) {
    // when
    BitbucketServerUrl bitbucketServerUrl = bitbucketServerURLParser.parse(repoUrl, null);

    // then
    assertEquals(bitbucketServerUrl.getProviderUrl(), "https://bitbucket.net");
  }

  @DataProvider
  public static Object[][] repoProvider() {
    return new Object[][] {
      {"https://bitbucket.net/scm/~user/repo.git", "https://bitbucket.net/scm/~user/repo.git"},
      {
        "https://bitbucket.net/users/user/repos/repo/browse?at=branch",
        "https://bitbucket.net/scm/~user/repo.git"
      },
      {"https://bitbucket.net/users/user/repos/repo", "https://bitbucket.net/scm/~user/repo.git"},
      {"https://bitbucket.net/scm/project/repo.git", "https://bitbucket.net/scm/project/repo.git"},
      {
        "https://bitbucket.net/projects/project/repos/repo/browse?at=branch",
        "https://bitbucket.net/scm/project/repo.git"
      },
      {
        "ssh://git@bitbucket.net:12345/project/repo.git",
        "ssh://git@bitbucket.net:12345/project/repo.git"
      },
      {
        "ssh://git@bitbucket.net:12345/~user/repo.git",
        "ssh://git@bitbucket.net:12345/~user/repo.git"
      },
    };
  }
}
