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
package org.eclipse.che.api.factory.server.github;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import java.lang.reflect.Field;
import java.util.Optional;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.commons.subject.Subject;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
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

  private GithubApiClient githubApiClient;

  private PersonalAccessTokenManager personalAccessTokenManager;

  /** Instance of component that will be tested. */
  private GithubURLParser githubUrlParser;

  WireMockServer wireMockServer;
  WireMock wireMock;

  /** Setup objects/ */
  @BeforeMethod
  protected void start() throws ApiException {
    wireMockServer =
        new WireMockServer(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort());
    wireMockServer.start();
    WireMock.configureFor("localhost", wireMockServer.port());
    wireMock = new WireMock("localhost", wireMockServer.port());
    this.personalAccessTokenManager = mock(PersonalAccessTokenManager.class);
    this.githubApiClient = mock(GithubApiClient.class);

    githubUrlParser =
        new GithubURLParser(
            personalAccessTokenManager,
            mock(DevfileFilenamesProvider.class),
            githubApiClient,
            null,
            false);
  }

  /** Check invalid url (not a GitHub one) */
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
  public void checkParsing(String url, String username, String repository, String branch)
      throws ApiException {
    GithubUrl githubUrl = githubUrlParser.parse(url);

    assertEquals(githubUrl.getUsername(), username);
    assertEquals(githubUrl.getRepository(), repository);
    assertEquals(githubUrl.getBranch(), branch);
  }

  /** Compare parsing */
  @Test(dataProvider = "parsingBadRepository")
  public void checkParsingBadRepositoryDoNotModifiesInitialInput(String url, String repository)
      throws ApiException {
    // given
    when(githubApiClient.isConnected(eq("https://github.com"))).thenReturn(true);

    // when
    GithubUrl githubUrl = githubUrlParser.parse(url);

    // then
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
      {"https://github.com/eclipse/che-with-hyphen.git"},
      {"git@github.com:eclipse/che.git)"},
      {"git@github.com:eclipse/che)"},
      {"git@github.com:eclipse/che123)"},
      {"git@github.com:eclipse/che.with.dots.git)"},
      {"git@github.com:eclipse/che-with-hyphen)"},
      {"git@github.com:eclipse/che-with-hyphen.git)"}
    };
  }

  @DataProvider(name = "parsing")
  public Object[][] expectedParsing() {
    return new Object[][] {
      {"https://github.com/eclipse/che", "eclipse", "che", null},
      {"https://github.com/eclipse/che123", "eclipse", "che123", null},
      {"https://github.com/eclipse/che.git", "eclipse", "che", null},
      {"https://github.com/eclipse/che.with.dot.git", "eclipse", "che.with.dot", null},
      {"https://github.com/eclipse/-.git", "eclipse", "-", null},
      {"https://github.com/eclipse/-j.git", "eclipse", "-j", null},
      {"https://github.com/eclipse/-", "eclipse", "-", null},
      {"https://github.com/eclipse/che-with-hyphen", "eclipse", "che-with-hyphen", null},
      {"https://github.com/eclipse/che-with-hyphen.git", "eclipse", "che-with-hyphen", null},
      {"https://github.com/eclipse/che/", "eclipse", "che", null},
      {"https://github.com/eclipse/repositorygit", "eclipse", "repositorygit", null},
      {"https://github.com/eclipse/che/tree/4.2.x", "eclipse", "che", "4.2.x"},
      {"https://github.com/eclipse/che/tree/master", "eclipse", "che", "master"},
      {
        "https://github.com/eclipse/che/tree/branch/with/slash",
        "eclipse",
        "che",
        "branch/with/slash"
      },
      {"git@github.com:eclipse/che", "eclipse", "che", null},
      {"git@github.com:eclipse/che123", "eclipse", "che123", null},
      {"git@github.com:eclipse/che.git", "eclipse", "che", null},
      {"git@github.com:eclipse/che.with.dot.git", "eclipse", "che.with.dot", null},
      {"git@github.com:eclipse/-.git", "eclipse", "-", null},
      {"git@github.com:eclipse/-j.git", "eclipse", "-j", null},
      {"git@github.com:eclipse/-", "eclipse", "-", null},
      {"git@github.com:eclipse/che-with-hyphen", "eclipse", "che-with-hyphen", null},
      {"git@github.com:eclipse/che-with-hyphen.git", "eclipse", "che-with-hyphen", null},
      {"git@github.com:eclipse/che/", "eclipse", "che", null},
      {"git@github.com:eclipse/repositorygit", "eclipse", "repositorygit", null},
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
      {"https://github.com/eclipse/ .git", " .git"},
      {"git@github.com:eclipse/che .git", "che .git"},
      {"git@github.com:eclipse/.git", ".git"},
      {"git@github.com:eclipse/myB@dR&pository.git", "myB@dR&pository.git"},
      {"git@github.com:eclipse/.", "."},
      {"git@github.com:eclipse/івапівап.git", "івапівап.git"},
      {"git@github.com:eclipse/ ", " "},
      {"git@github.com:eclipse/.", "."},
      {"git@github.com:eclipse/ .git", " .git"}
    };
  }

  /** Check Pull Request with data inside the repository */
  @Test
  public void checkPullRequestFromRepository() throws Exception {
    String url = "https://github.com/eclipse/che/pull/21276";

    GithubPullRequest pr =
        new GithubPullRequest()
            .withState("open")
            .withHead(
                new GithubHead()
                    .withRef("pr-main-to-7.46.0")
                    .withUser(new GithubUser().withId(0).withName("eclipse").withLogin("eclipse"))
                    .withRepo(new GithubRepo().withName("che")));
    when(githubApiClient.isConnected(eq("https://github.com"))).thenReturn(true);
    when(githubApiClient.getPullRequest(any(), any(), any(), any())).thenReturn(pr);

    GithubUrl githubUrl = githubUrlParser.parse(url);

    assertEquals(githubUrl.getUsername(), "eclipse");
    assertEquals(githubUrl.getRepository(), "che");
    assertEquals(githubUrl.getBranch(), "pr-main-to-7.46.0");
  }

  /** Check Pull Request with data outside the repository (fork) */
  @Test
  public void checkPullRequestFromForkedRepository() throws Exception {
    GithubPullRequest pr =
        new GithubPullRequest()
            .withState("open")
            .withHead(
                new GithubHead()
                    .withRef("main")
                    .withUser(new GithubUser().withLogin("eclipse"))
                    .withRepo(new GithubRepo().withName("che")));

    PersonalAccessToken personalAccessToken = mock(PersonalAccessToken.class);
    when(personalAccessToken.getToken()).thenReturn("token");
    when(personalAccessTokenManager.get(any(Subject.class), anyString()))
        .thenReturn(Optional.of(personalAccessToken));

    when(githubApiClient.isConnected(eq("https://github.com"))).thenReturn(true);
    when(githubApiClient.getPullRequest(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(pr);

    String url = "https://github.com/eclipse/che/pull/20189";
    GithubUrl githubUrl = githubUrlParser.parse(url);

    assertEquals(githubUrl.getUsername(), "eclipse");
    assertEquals(githubUrl.getRepository(), "che");
    assertEquals(githubUrl.getBranch(), "main");
  }

  @Test
  public void checkPullRequestFromForkedRepositoryWithoutAuthentication() throws Exception {
    String url = "https://github.com/eclipse/che/pull/21276";

    GithubPullRequest pr =
        new GithubPullRequest()
            .withState("open")
            .withHead(
                new GithubHead()
                    .withRef("pr-main-to-7.46.0-SNAPSHOT")
                    .withUser(new GithubUser().withId(0).withName("eclipse").withLogin("eclipse"))
                    .withRepo(new GithubRepo().withName("che")));

    when(githubApiClient.isConnected(eq("https://github.com"))).thenReturn(true);
    when(githubApiClient.getPullRequest(any(), any(), any(), any())).thenReturn(pr);

    GithubUrl githubUrl = githubUrlParser.parseWithoutAuthentication(url);

    assertEquals(githubUrl.getUsername(), "eclipse");
    assertEquals(githubUrl.getRepository(), "che");
    assertEquals(githubUrl.getBranch(), "pr-main-to-7.46.0-SNAPSHOT");

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
    when(githubApiClient.isConnected(eq("https://github.com"))).thenReturn(true);
    when(githubApiClient.getPullRequest(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(githubPullRequest);

    String url = "https://github.com/eclipse/che/pull/11103";
    githubUrlParser.parse(url);
  }

  @Test
  public void shouldParseServerUr() throws Exception {
    // given
    String url = "https://github-server.com/user/repo";

    // when
    GithubUrl githubUrl = githubUrlParser.parse(url);

    // then
    assertEquals(githubUrl.getUsername(), "user");
    assertEquals(githubUrl.getRepository(), "repo");
    assertEquals(githubUrl.getHostName(), "https://github-server.com");
  }

  @Test
  public void shouldParseServerUrWithPullRequestId() throws Exception {
    // given
    String url = "https://github-server.com/user/repo/pull/11103";
    GithubPullRequest pr =
        new GithubPullRequest()
            .withState("open")
            .withHead(
                new GithubHead()
                    .withUser(new GithubUser().withId(0).withName("eclipse").withLogin("eclipse"))
                    .withRepo(new GithubRepo().withName("che")));
    when(githubApiClient.isConnected(eq("https://github-server.com"))).thenReturn(true);
    when(githubApiClient.getPullRequest(any(), any(), any(), any())).thenReturn(pr);

    // when
    githubUrlParser.parse(url);

    // then
    verify(personalAccessTokenManager, times(2))
        .get(any(Subject.class), eq("https://github-server.com"));
  }

  @Test
  public void shouldValidateOldVersionGitHubServerUrl() throws Exception {
    // given
    Field endpoint = AbstractGithubURLParser.class.getDeclaredField("endpoint");
    endpoint.setAccessible(true);
    endpoint.set(githubUrlParser, wireMockServer.baseUrl());
    String url = wireMockServer.url("/user/repo");
    stubFor(
        get(urlEqualTo("/api/v3/user"))
            .willReturn(
                aResponse()
                    .withStatus(HTTP_UNAUTHORIZED)
                    .withBody("{\"message\": \"Must authenticate to access this API.\",\n}")));

    // when
    boolean valid = githubUrlParser.isValid(url);

    // then
    assertTrue(valid);
  }

  @Test
  public void shouldValidateGitHubServerUrl() throws Exception {
    // given
    Field endpoint = AbstractGithubURLParser.class.getDeclaredField("endpoint");
    endpoint.setAccessible(true);
    endpoint.set(githubUrlParser, wireMockServer.baseUrl());
    String url = wireMockServer.url("/user/repo");
    stubFor(
        get(urlEqualTo("/api/v3/user"))
            .willReturn(
                aResponse()
                    .withStatus(HTTP_UNAUTHORIZED)
                    .withBody("{\"message\": \"Requires authentication\",\n}")));

    // when
    boolean valid = githubUrlParser.isValid(url);

    // then
    assertTrue(valid);
  }

  @Test
  public void shouldNotRequestGitHubSAASUrl() throws Exception {
    // when
    githubUrlParser.isValid("https:github.com/repo/user.git");

    // then
    verify(githubApiClient, never()).getUser(anyString());
  }
}
