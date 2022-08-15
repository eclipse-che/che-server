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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Optional;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.commons.subject.Subject;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Validate operations performed by the Github parser
 *
 * @author Florent Benoit
 */
@Listeners(MockitoTestNGListener.class)
public class GithubURLParserTest {

  @Mock private DevfileFilenamesProvider devfileFilenamesProvider;

  /** Instance of component that will be tested. */
  @InjectMocks private GithubURLParser githubUrlParser;

  @InjectMocks
  private PersonalAccessTokenManager personalAccessTokenManager =
      mock(PersonalAccessTokenManager.class);

  @InjectMocks private GithubApiClient githubApiClient = mock(GithubApiClient.class);

  /** Check invalid url (not a github one) */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void invalidUrl() throws ApiException {
    githubUrlParser.parse("http://www.eclipse.org");
  }

  /** Check URLs are valid with regexp */
  @Test(dataProvider = "UrlsProvider")
  public void checkRegexp(String url) {
    assertTrue(githubUrlParser.isValid(url), "url " + url + " is invalid");
  }

  /** Compare parsing */
  @Test(dataProvider = "parsing")
  public void checkParsing(
      String url, String username, String repository, String branch, String subfolder)
      throws ApiException {
    GithubUrl githubUrl = githubUrlParser.parse(url);

    assertEquals(githubUrl.getUsername(), username);
    assertEquals(githubUrl.getRepository(), repository);
    assertEquals(githubUrl.getBranch(), branch);
    assertEquals(githubUrl.getSubfolder(), subfolder);
  }

  /** Compare parsing */
  @Test(dataProvider = "parsingBadRepository")
  public void checkParsingBadRepositoryDoNotModifiesInitialInput(String url, String repository)
      throws ApiException {
    GithubUrl githubUrl = githubUrlParser.parse(url);
    assertEquals(githubUrl.getRepository(), repository);
  }

  @DataProvider(name = "UrlsProvider")
  public Object[][] urls() {
    return new Object[][] {
      {"https://github.com/eclipse/che"},
      {"https://github.com/eclipse/che123"},
      {"https://github.com/eclipse/che/"},
      {"https://github.com/eclipse/che/tree/4.2.x"},
      {"https://github.com/eclipse/che/tree/master/"},
      {"https://github.com/eclipse/che/tree/master/dashboard/"},
      {"https://github.com/eclipse/che/tree/master/plugins/plugin-git/che-plugin-git-ext-git"},
      {"https://github.com/eclipse/che/tree/master/plugins/plugin-git/che-plugin-git-ext-git/"},
      {"https://github.com/eclipse/che/pull/11103"},
      {"https://github.com/eclipse/che.git"},
      {"https://github.com/eclipse/che.with.dots.git"},
      {"https://github.com/eclipse/che-with-hyphen"},
      {"https://github.com/eclipse/che-with-hyphen.git"}
    };
  }

  @DataProvider(name = "parsing")
  public Object[][] expectedParsing() {
    return new Object[][] {
      {"https://github.com/eclipse/che", "eclipse", "che", null, null},
      {"https://github.com/eclipse/che123", "eclipse", "che123", null, null},
      {"https://github.com/eclipse/che.git", "eclipse", "che", null, null},
      {"https://github.com/eclipse/che.with.dot.git", "eclipse", "che.with.dot", null, null},
      {"https://github.com/eclipse/-.git", "eclipse", "-", null, null},
      {"https://github.com/eclipse/-j.git", "eclipse", "-j", null, null},
      {"https://github.com/eclipse/-", "eclipse", "-", null, null},
      {"https://github.com/eclipse/che-with-hyphen", "eclipse", "che-with-hyphen", null, null},
      {"https://github.com/eclipse/che-with-hyphen.git", "eclipse", "che-with-hyphen", null, null},
      {"https://github.com/eclipse/che/", "eclipse", "che", null, null},
      {"https://github.com/eclipse/repositorygit", "eclipse", "repositorygit", null, null},
      {"https://github.com/eclipse/che/tree/4.2.x", "eclipse", "che", "4.2.x", null},
      {
        "https://github.com/eclipse/che/tree/master/dashboard/",
        "eclipse",
        "che",
        "master",
        "dashboard/"
      },
      {
        "https://github.com/eclipse/che/tree/master/plugins/plugin-git/che-plugin-git-ext-git",
        "eclipse",
        "che",
        "master",
        "plugins/plugin-git/che-plugin-git-ext-git"
      }
    };
  }

  @DataProvider(name = "parsingBadRepository")
  public Object[][] parsingBadRepository() {
    return new Object[][] {
      {"https://github.com/eclipse/che .git", "che .git"},
      {"https://github.com/eclipse/.git", ".git"},
      {"https://github.com/eclipse/myB@dR&pository.git", "myB@dR&pository.git"},
      {"https://github.com/eclipse/.", "."},
      {"https://github.com/eclipse/івапівап.git", "івапівап.git"},
      {"https://github.com/eclipse/ ", " "},
      {"https://github.com/eclipse/.", "."},
      {"https://github.com/eclipse/ .git", " .git"}
    };
  }

  /** Check Pull Request with data inside the repository */
  @Test
  public void checkPullRequestFromRepository() throws ApiException {

    String url = "https://github.com/eclipse/che/pull/21276";
    GithubUrl githubUrl = githubUrlParser.parse(url);

    assertEquals(githubUrl.getUsername(), "eclipse");
    assertEquals(githubUrl.getRepository(), "che");
    assertEquals(githubUrl.getBranch(), "main");
  }

  /** Check Pull Request with data outside the repository (fork) */
  @Test
  public void checkPullRequestFromForkedRepository() throws Exception {
    PersonalAccessToken personalAccessToken = mock(PersonalAccessToken.class);
    GithubPullRequest githubPullRequest = mock(GithubPullRequest.class);
    GithubHead githubHead = mock(GithubHead.class);
    GithubRepo githubRepo = mock(GithubRepo.class);
    GithubUser githubUser = mock(GithubUser.class);
    when(githubUser.getLogin()).thenReturn("eclipse");
    when(githubRepo.getName()).thenReturn("che");
    when(githubHead.getRef()).thenReturn("main");
    when(githubHead.getRepo()).thenReturn(githubRepo);
    when(githubHead.getUser()).thenReturn(githubUser);
    when(githubPullRequest.getState()).thenReturn("open");
    when(githubPullRequest.getHead()).thenReturn(githubHead);
    when(personalAccessToken.getToken()).thenReturn("token");
    when(personalAccessTokenManager.get(any(Subject.class), anyString()))
        .thenReturn(Optional.of(personalAccessToken));
    when(githubApiClient.getPullRequest(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(githubPullRequest);

    String url = "https://github.com/eclipse/che/pull/20189";
    GithubUrl githubUrl = githubUrlParser.parse(url);

    assertEquals(githubUrl.getUsername(), "eclipse");
    assertEquals(githubUrl.getRepository(), "che");
    assertEquals(githubUrl.getBranch(), "main");
  }

  @Test
  public void checkPullRequestFromForkedRepositoryWithoutAuthentication() throws Exception {
    String url = "https://github.com/eclipse/che/pull/21276";
    GithubUrl githubUrl = githubUrlParser.parseWithoutAuthentication(url);

    assertEquals(githubUrl.getUsername(), "eclipse");
    assertEquals(githubUrl.getRepository(), "che");
    verify(personalAccessTokenManager, never()).fetchAndSave(any(Subject.class), anyString());
  }

  /** Check Pull Request is failing with Merged state */
  @Test(
      expectedExceptions = IllegalArgumentException.class,
      expectedExceptionsMessageRegExp = ".*found merged.*")
  public void checkPullRequestMergedState() throws Exception {
    PersonalAccessToken personalAccessToken = mock(PersonalAccessToken.class);
    GithubPullRequest githubPullRequest = mock(GithubPullRequest.class);
    when(githubPullRequest.getState()).thenReturn("merged");
    when(personalAccessToken.getToken()).thenReturn("token");
    when(personalAccessTokenManager.get(any(Subject.class), anyString()))
        .thenReturn(Optional.of(personalAccessToken));
    when(githubApiClient.getPullRequest(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(githubPullRequest);

    String url = "https://github.com/eclipse/che/pull/11103";
    githubUrlParser.parse(url);
  }
}
