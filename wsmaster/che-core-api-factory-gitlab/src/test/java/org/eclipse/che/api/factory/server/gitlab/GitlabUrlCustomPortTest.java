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
package org.eclipse.che.api.factory.server.gitlab;

import static java.lang.String.format;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.util.Arrays;
import java.util.Iterator;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl.DevfileLocation;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Test of {@Link GitlabUrl} Note: The parser is also testing the {@code GitlabURLParser} object
 *
 * @author Florent Benoit
 */
@Listeners(MockitoTestNGListener.class)
public class GitlabUrlCustomPortTest {

  @Mock private DevfileFilenamesProvider devfileFilenamesProvider;

  /** Parser used to create the url. */
  private GitlabUrlParser gitlabUrlParser;

  /** Setup objects/ */
  @BeforeMethod
  protected void init() {
    when(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .thenReturn(Arrays.asList("devfile.yaml", "foo.bar"));
    gitlabUrlParser =
        new GitlabUrlParser(
            "https://gitlab.net:3120",
            devfileFilenamesProvider,
            mock(PersonalAccessTokenManager.class));
  }

  /** Check when there is devfile in the repository */
  @Test(dataProvider = "urlsProvider")
  public void checkDevfileLocation(String repoUrl, String fileUrl) {
    lenient()
        .when(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .thenReturn(Arrays.asList("devfile.yaml", "foo.bar"));

    GitlabUrl gitlabUrl = gitlabUrlParser.parse(repoUrl);
    assertEquals(gitlabUrl.devfileFileLocations().size(), 2);
    Iterator<DevfileLocation> iterator = gitlabUrl.devfileFileLocations().iterator();
    assertEquals(iterator.next().location(), format(fileUrl, "devfile.yaml"));
    assertEquals(iterator.next().location(), format(fileUrl, "foo.bar"));
  }

  @Test(dataProvider = "urlsProvider")
  public void shouldReturnProviderUrl(String repoUrl, String ignored) {
    // when
    GitlabUrl gitlabUrl = gitlabUrlParser.parse(repoUrl);

    // then
    assertEquals(gitlabUrl.getProviderUrl(), "https://gitlab.net:3120");
  }

  @DataProvider
  public static Object[][] urlsProvider() {
    return new Object[][] {
      {
        "https://gitlab.net:3120/eclipse/che.git",
        "https://gitlab.net:3120/api/v4/projects/eclipse%%2Fche/repository/files/%s/raw?ref=HEAD"
      },
      {
        "https://gitlab.net:3120/eclipse/fooproj/che.git",
        "https://gitlab.net:3120/api/v4/projects/eclipse%%2Ffooproj%%2Fche/repository/files/%s/raw?ref=HEAD"
      },
      // {
      //   "git@gitlab.net:eclipse/che.git",
      //   "https://gitlab.net:3120/api/v4/projects/eclipse%%2Fche/repository/files/%s/raw?ref=HEAD"
      // },
      // {
      //   "git@gitlab.net:eclipse/fooproj/che.git",
      //
      // "https://gitlab.net:3120/api/v4/projects/eclipse%%2Ffooproj%%2Fche/repository/files/%s/raw?ref=HEAD"
      // },
      {
        "https://gitlab.net:3120/eclipse/fooproj/-/tree/master/",
        "https://gitlab.net:3120/api/v4/projects/eclipse%%2Ffooproj/repository/files/%s/raw?ref=master"
      },
      {
        "https://gitlab.net:3120/eclipse/fooproj/che/-/tree/foobranch/",
        "https://gitlab.net:3120/api/v4/projects/eclipse%%2Ffooproj%%2Fche/repository/files/%s/raw?ref=foobranch"
      },
    };
  }

  /** Check the original repository */
  @Test(dataProvider = "repoProvider2")
  public void checkRepositoryLocation(String rawUrl, String repoUrl) {
    GitlabUrl gitlabUrl = gitlabUrlParser.parse(rawUrl);
    assertEquals(gitlabUrl.repositoryLocation(), repoUrl);
  }

  @DataProvider
  public static Object[][] repoProvider2() {
    return new Object[][] {
      {"https://gitlab.net:3120/eclipse/che.git", "https://gitlab.net:3120/eclipse/che.git"},
      {
        "https://gitlab.net:3120/eclipse/foo/che.git", "https://gitlab.net:3120/eclipse/foo/che.git"
      },
      {"git@gitlab.net:eclipse/che.git", "git@gitlab.net:eclipse/che.git"},
      {"git@gitlab.net:eclipse/foo/che.git", "git@gitlab.net:eclipse/foo/che.git"},
      {
        "https://gitlab.net:3120/eclipse/fooproj/che/-/tree/master/",
        "https://gitlab.net:3120/eclipse/fooproj/che.git"
      }
    };
  }
}
