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
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Test of {@Link GithubUrl} Note: The parser is also testing the object
 *
 * @author Florent Benoit
 */
@Listeners(MockitoTestNGListener.class)
public class GithubUrlTest {

  @Mock private GithubApiClient githubApiClient;

  /** Check when there is devfile in the repository */
  @Test
  public void checkDevfileLocation() throws Exception {
    DevfileFilenamesProvider devfileFilenamesProvider = mock(DevfileFilenamesProvider.class);

    /** Parser used to create the url. */
    GithubURLParser githubUrlParser =
        new GithubURLParser(
            mock(PersonalAccessTokenManager.class),
            devfileFilenamesProvider,
            githubApiClient,
            null,
            false);

    when(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .thenReturn(Arrays.asList("devfile.yaml", "foo.bar"));

    GithubUrl githubUrl = githubUrlParser.parse("https://github.com/eclipse/che");

    assertEquals(githubUrl.devfileFileLocations().size(), 2);
    Iterator<DevfileLocation> iterator = githubUrl.devfileFileLocations().iterator();
    assertEquals(
        iterator.next().location(),
        "https://raw.githubusercontent.com/eclipse/che/HEAD/devfile.yaml");

    assertEquals(
        iterator.next().location(), "https://raw.githubusercontent.com/eclipse/che/HEAD/foo.bar");
  }

  /** Check the original repository */
  @Test
  public void checkRepositoryLocation() throws Exception {
    DevfileFilenamesProvider devfileFilenamesProvider = mock(DevfileFilenamesProvider.class);

    /** Parser used to create the url. */
    GithubURLParser githubUrlParser =
        new GithubURLParser(
            mock(PersonalAccessTokenManager.class),
            devfileFilenamesProvider,
            githubApiClient,
            null,
            false);

    when(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .thenReturn(Arrays.asList("devfile.yaml", "foo.bar"));

    GithubUrl githubUrl = githubUrlParser.parse("https://github.com/eclipse/che");

    assertEquals(githubUrl.repositoryLocation(), "https://github.com/eclipse/che.git");
  }

  @Test
  public void testRawFileLocationWithDefaultBranchName() {
    String file = ".che/che-theia-plugins.yaml";

    GithubUrl url = new GithubUrl().withUsername("eclipse").withRepository("che");

    assertEquals(
        url.rawFileLocation(file),
        "https://raw.githubusercontent.com/eclipse/che/HEAD/.che/che-theia-plugins.yaml");
  }

  @Test
  public void testRawFileLocationWithCustomBranchName() {
    String file = ".che/che-theia-plugins.yaml";

    GithubUrl url =
        new GithubUrl().withUsername("eclipse").withRepository("che").withBranch("main");

    assertEquals(
        url.rawFileLocation(file),
        "https://raw.githubusercontent.com/eclipse/che/main/.che/che-theia-plugins.yaml");
  }

  @Test
  public void testRawFileLocationForCommit() {
    String file = ".che/che-theia-plugins.yaml";

    GithubUrl url =
        new GithubUrl()
            .withUsername("eclipse")
            .withRepository("che")
            .withBranch("main")
            .withLatestCommit("c24fd44e0f7296be2e49a380fb8abe2fe4db9100");

    assertEquals(
        url.rawFileLocation(file),
        "https://raw.githubusercontent.com/eclipse/che/c24fd44e0f7296be2e49a380fb8abe2fe4db9100/.che/che-theia-plugins.yaml");
  }
}
