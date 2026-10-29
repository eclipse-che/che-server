/*
 * Copyright (c) 2012-2026 Red Hat, Inc.
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
import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import java.lang.reflect.Field;
import java.util.Optional;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.commons.lang.UrlTargetValidator;
import org.eclipse.che.commons.subject.Subject;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
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
  @Mock private DevfileFilenamesProvider devfileFilenamesProvider;

  /** Instance of component that will be tested. */
  private GithubURLParser githubUrlParser;

  WireMockServer wireMockServer;
  WireMock wireMock;

  @AfterMethod
  protected void restoreTrustedHosts() {
    UrlTargetValidator.setTrustedHosts(null);
  }

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
            personalAccessTokenManager, devfileFilenamesProvider, githubApiClient, null, false);
  }

  /** Check invalid url (not a GitHub one) */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void invalidUrl() throws ApiException {
    githubUrlParser.parse("http://www.eclipse.org", null);
  }

  @Test
  public void shouldParseWithBranch() throws ApiException {
    GithubUrl githubUrl = githubUrlParser.parse("https://github.com/eclipse/che", "branch");
    assertEquals(githubUrl.getBranch(), "branch");
  }

  @Test
  public void shouldParseWithUrlBranch() throws ApiException {
    GithubUrl githubUrl =
        githubUrlParser.parse("https://github.com/eclipse/che/tree/master/", "branch");
    assertEquals(githubUrl.getBranch(), "master");
  }

  /**
   * Makes the API answer as a repository whose only refs are {@code refs}, so that splitting a
   * `tree` URL into a branch and a subfolder is decided here and not over the network.
   */
  private void givenRefs(String... refs) throws Exception {
    when(githubApiClient.isConnected(eq("https://github.com"))).thenReturn(true);
    when(githubApiClient.getLatestCommit(anyString(), anyString(), anyString(), any()))
        .thenAnswer(
            invocation -> {
              String ref = invocation.getArgument(2);
              for (String existing : refs) {
                if (existing.equals(ref)) {
                  return new GithubCommit().withSha("sha-of-" + existing);
                }
              }
              throw new ScmItemNotFoundException("No commit found for " + ref);
            });
  }

  @Test
  public void shouldSplitTheSubfolderOffTheBranch() throws Exception {
    givenRefs("main");

    GithubUrl githubUrl =
        githubUrlParser.parse("https://github.com/eclipse/che/tree/main/sub/dir", null);

    assertEquals(githubUrl.getBranch(), "main");
    assertEquals(githubUrl.getSubfolder(), "sub/dir");
  }

  /** The case the subfolder split must not break: every segment belongs to the branch. */
  @Test
  public void shouldKeepABranchWhoseNameHoldsSlashesWhole() throws Exception {
    givenRefs("branch/with/slash");

    GithubUrl githubUrl =
        githubUrlParser.parse("https://github.com/eclipse/che/tree/branch/with/slash", null);

    assertEquals(githubUrl.getBranch(), "branch/with/slash");
    assertNull(githubUrl.getSubfolder());
  }

  @Test
  public void shouldLeaveTheDevfileNameOutOfTheSubfolder() throws Exception {
    when(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .thenReturn(asList("devfile.yaml", ".devfile.yaml"));
    givenRefs("main");

    GithubUrl githubUrl =
        githubUrlParser.parse("https://github.com/eclipse/che/blob/main/sub/devfile.yaml", null);

    assertEquals(githubUrl.getBranch(), "main");
    assertEquals(githubUrl.getSubfolder(), "sub");
  }

  /** A directory named after the devfile must not be cut at its first occurrence. */
  @Test
  public void shouldStripOnlyTheTrailingDevfileName() throws Exception {
    when(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .thenReturn(asList("devfile.yaml", ".devfile.yaml"));
    givenRefs("main");

    GithubUrl githubUrl =
        githubUrlParser.parse(
            "https://github.com/eclipse/che/blob/main/devfile.yaml/sub/devfile.yaml", null);

    assertEquals(githubUrl.getBranch(), "main");
    assertEquals(githubUrl.getSubfolder(), "devfile.yaml/sub");
  }

  /** An unreachable API must leave the URL parsed the way it was before the split existed. */
  @Test
  public void shouldKeepTheWholeTailWhenTheApiCannotAnswer() throws Exception {
    when(githubApiClient.isConnected(eq("https://github.com"))).thenReturn(true);
    when(githubApiClient.getLatestCommit(anyString(), anyString(), anyString(), any()))
        .thenThrow(new ScmCommunicationException("GitHub is unreachable"));

    GithubUrl githubUrl =
        githubUrlParser.parse("https://github.com/eclipse/che/tree/main/sub/dir", null);

    assertEquals(githubUrl.getBranch(), "main/sub/dir");
    assertNull(githubUrl.getSubfolder());
  }

  @Test
  public void shouldKeepTheWholeTailWhenNoPrefixIsARef() throws Exception {
    givenRefs();

    GithubUrl githubUrl =
        githubUrlParser.parse("https://github.com/eclipse/che/tree/gone/sub/dir", null);

    assertEquals(githubUrl.getBranch(), "gone/sub/dir");
    assertNull(githubUrl.getSubfolder());
  }

  /** The probe that found the branch is the commit lookup, so it must not be repeated. */
  @Test
  public void shouldReuseTheProbeThatFoundTheBranch() throws Exception {
    givenRefs("main");

    GithubUrl githubUrl =
        githubUrlParser.parse("https://github.com/eclipse/che/tree/main/sub/dir", null);

    assertEquals(githubUrl.getLatestCommit(), "sha-of-main");
    verify(githubApiClient, times(1)).getLatestCommit(anyString(), anyString(), anyString(), any());
  }

  /** An explicit revision wins over the URL, so the branch it names is the one looked up. */
  @Test
  public void shouldLookUpTheExplicitRevisionRatherThanTheProbe() throws Exception {
    givenRefs("main", "other");

    GithubUrl githubUrl =
        githubUrlParser.parse("https://github.com/eclipse/che/tree/main/sub/dir", "other");

    assertEquals(githubUrl.getBranch(), "main");
    assertEquals(githubUrl.getSubfolder(), "sub/dir");
    assertEquals(githubUrl.getLatestCommit(), "sha-of-other");
  }

  @Test
  public void shouldNotProbeAPullRequestUrl() throws Exception {
    GithubPullRequest pullRequest =
        new GithubPullRequest()
            .withState("open")
            .withHead(
                new GithubHead()
                    .withRef("pr-branch")
                    .withUser(new GithubUser().withId(0).withName("eclipse").withLogin("eclipse"))
                    .withRepo(new GithubRepo().withName("che")));
    when(githubApiClient.isConnected(eq("https://github.com"))).thenReturn(true);
    when(githubApiClient.getPullRequest(any(), any(), any(), any())).thenReturn(pullRequest);

    GithubUrl githubUrl = githubUrlParser.parse("https://github.com/eclipse/che/pull/123", null);

    assertEquals(githubUrl.getBranch(), "pr-branch");
    assertNull(githubUrl.getSubfolder());
  }

  /** A deep path matching no ref must not turn into one API call per segment. */
  @Test
  public void shouldCapTheNumberOfProbes() throws Exception {
    givenRefs();

    GithubUrl githubUrl =
        githubUrlParser.parse("https://github.com/eclipse/che/tree/a/b/c/d/e/f/g/h/i/j/k/l", null);

    assertEquals(githubUrl.getBranch(), "a/b/c/d/e/f/g/h/i/j/k/l");
    // Eight probes rather than twelve, plus the commit lookup every parse ends with.
    verify(githubApiClient, times(9)).getLatestCommit(anyString(), anyString(), anyString(), any());
  }

  @Test
  public void shouldParseGithubServerUrlWithIpv6Host() throws ApiException {
    // given
    githubUrlParser =
        new GithubURLParser(
            personalAccessTokenManager,
            devfileFilenamesProvider,
            githubApiClient,
            "https://[2001:db8::1]",
            false);
    when(githubApiClient.isConnected(eq("https://[2001:db8::1]"))).thenReturn(true);
    when(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .thenReturn(asList("devfile.yaml", ".devfile.yaml"));

    // when
    GithubUrl githubUrl = githubUrlParser.parse("https://[2001:db8::1]/eclipse/che", null);

    // then
    assertEquals(githubUrl.getUsername(), "eclipse");
    assertEquals(githubUrl.getRepository(), "che");
  }

  @Test
  public void shouldParseIpv6UrlWithBranch() throws ApiException {
    // given
    githubUrlParser =
        new GithubURLParser(
            personalAccessTokenManager,
            devfileFilenamesProvider,
            githubApiClient,
            "https://[2001:db8::1]",
            false);
    when(githubApiClient.isConnected(eq("https://[2001:db8::1]"))).thenReturn(true);
    when(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .thenReturn(asList("devfile.yaml", ".devfile.yaml"));

    // when
    GithubUrl githubUrl =
        githubUrlParser.parse("https://[2001:db8::1]/eclipse/che/tree/feature-branch", null);

    // then
    assertEquals(githubUrl.getUsername(), "eclipse");
    assertEquals(githubUrl.getRepository(), "che");
    assertEquals(githubUrl.getBranch(), "feature-branch");
  }

  @Test
  public void shouldParseIpv6UrlWithRevisionParam() throws ApiException {
    // given
    githubUrlParser =
        new GithubURLParser(
            personalAccessTokenManager,
            devfileFilenamesProvider,
            githubApiClient,
            "https://[2001:db8::1]",
            false);
    when(githubApiClient.isConnected(eq("https://[2001:db8::1]"))).thenReturn(true);
    when(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .thenReturn(asList("devfile.yaml", ".devfile.yaml"));

    // when
    GithubUrl githubUrl = githubUrlParser.parse("https://[2001:db8::1]/eclipse/che", "my-branch");

    // then
    assertEquals(githubUrl.getUsername(), "eclipse");
    assertEquals(githubUrl.getRepository(), "che");
    assertEquals(githubUrl.getBranch(), "my-branch");
  }

  @Test
  public void shouldParseIpv6UrlWithPullRequestId() throws Exception {
    // given
    githubUrlParser =
        new GithubURLParser(
            personalAccessTokenManager,
            devfileFilenamesProvider,
            githubApiClient,
            "https://[2001:db8::1]",
            false);

    GithubPullRequest pr =
        new GithubPullRequest()
            .withState("open")
            .withHead(
                new GithubHead()
                    .withRef("pr-branch")
                    .withUser(new GithubUser().withId(0).withName("eclipse").withLogin("eclipse"))
                    .withRepo(new GithubRepo().withName("che")));
    when(githubApiClient.isConnected(eq("https://[2001:db8::1]"))).thenReturn(true);
    when(githubApiClient.getPullRequest(any(), any(), any(), any())).thenReturn(pr);

    // when
    GithubUrl githubUrl = githubUrlParser.parse("https://[2001:db8::1]/eclipse/che/pull/123", null);

    // then
    assertEquals(githubUrl.getUsername(), "eclipse");
    assertEquals(githubUrl.getRepository(), "che");
    assertEquals(githubUrl.getBranch(), "pr-branch");
  }

  @Test
  public void shouldParseIpv6LoopbackAddress() throws ApiException {
    // given
    githubUrlParser =
        new GithubURLParser(
            personalAccessTokenManager,
            devfileFilenamesProvider,
            githubApiClient,
            "https://[::1]",
            false);
    when(githubApiClient.isConnected(eq("https://[::1]"))).thenReturn(true);
    when(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .thenReturn(asList("devfile.yaml", ".devfile.yaml"));

    // when
    GithubUrl githubUrl = githubUrlParser.parse("https://[::1]/eclipse/che", null);

    // then
    assertEquals(githubUrl.getUsername(), "eclipse");
    assertEquals(githubUrl.getRepository(), "che");
  }

  @Test
  public void shouldParseIpv6FullFormAddress() throws ApiException {
    // given
    githubUrlParser =
        new GithubURLParser(
            personalAccessTokenManager,
            devfileFilenamesProvider,
            githubApiClient,
            "https://[2001:0db8:0000:0000:0000:0000:0000:0001]",
            false);
    when(githubApiClient.isConnected(eq("https://[2001:0db8:0000:0000:0000:0000:0000:0001]")))
        .thenReturn(true);
    when(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .thenReturn(asList("devfile.yaml", ".devfile.yaml"));

    // when
    GithubUrl githubUrl =
        githubUrlParser.parse(
            "https://[2001:0db8:0000:0000:0000:0000:0000:0001]/eclipse/che", null);

    // then
    assertEquals(githubUrl.getUsername(), "eclipse");
    assertEquals(githubUrl.getRepository(), "che");
  }

  @Test
  public void shouldParseIpv6DotGitUrl() throws ApiException {
    // given
    githubUrlParser =
        new GithubURLParser(
            personalAccessTokenManager,
            devfileFilenamesProvider,
            githubApiClient,
            "https://[2001:db8::1]",
            false);
    when(githubApiClient.isConnected(eq("https://[2001:db8::1]"))).thenReturn(true);
    when(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .thenReturn(asList("devfile.yaml", ".devfile.yaml"));

    // when
    GithubUrl githubUrl = githubUrlParser.parse("https://[2001:db8::1]/eclipse/che.git", null);

    // then
    assertEquals(githubUrl.getUsername(), "eclipse");
    assertEquals(githubUrl.getRepository(), "che");
  }

  @Test
  public void shouldParseIpv6UrlViaDynamicPatternMatching() throws ApiException {
    // The parser is configured for github.com, but getPatternMatcherByUrl() dynamically
    // creates a pattern from the URL itself, so IPv6 URLs can be parsed even when the
    // constructor was configured with a different host.
    when(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .thenReturn(asList("devfile.yaml", ".devfile.yaml"));

    GithubUrl githubUrl = githubUrlParser.parse("https://[2001:db8::1]/eclipse/che", null);

    assertEquals(githubUrl.getUsername(), "eclipse");
    assertEquals(githubUrl.getRepository(), "che");
  }

  /** Check URLs are valid with regexp */
  @Test(dataProvider = "UrlsProvider")
  public void checkRegexp(String url) {
    assertTrue(githubUrlParser.isValid(url), "url " + url + " is invalid");
  }

  /**
   * Compare parsing. The API is made unreachable so that the rows below describe what the parser
   * makes of a URL on its own, without reaching github.com from a unit test; a tail that holds
   * slashes is then kept whole, since nothing can confirm where the branch ends. The rows that pin
   * down the branch and subfolder split are the dedicated tests above.
   */
  @Test(dataProvider = "parsing")
  public void checkParsing(String url, String username, String repository, String path)
      throws Exception {
    when(devfileFilenamesProvider.getConfiguredDevfileFilenames())
        .thenReturn(asList("devfile.yaml", ".devfile.yaml"));
    when(githubApiClient.isConnected(anyString())).thenReturn(true);
    when(githubApiClient.getLatestCommit(anyString(), anyString(), anyString(), any()))
        .thenThrow(new ScmCommunicationException("GitHub is unreachable"));

    GithubUrl githubUrl = githubUrlParser.parse(url, null);

    assertEquals(githubUrl.getUsername(), username);
    assertEquals(githubUrl.getRepository(), repository);
    assertEquals(githubUrl.getBranch(), path);
  }

  /** Compare parsing */
  @Test(dataProvider = "parsingBadRepository")
  public void checkParsingBadRepositoryDoNotModifiesInitialInput(String url, String repository)
      throws ApiException {
    // given
    when(githubApiClient.isConnected(eq("https://github.com"))).thenReturn(true);

    // when
    GithubUrl githubUrl = githubUrlParser.parse(url, null);

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
      {"https://github.com/eclipse/che/tree/master/", "eclipse", "che", "master"},
      {
        "https://github.com/eclipse/che/tree/branch/with/slash",
        "eclipse",
        "che",
        "branch/with/slash"
      },
      {"https://github.com/eclipse/che/blob/branch/path/to/", "eclipse", "che", "branch/path/to"},
      {
        "https://github.com/eclipse/che/blob/branch/path/to/devfile.yaml",
        "eclipse",
        "che",
        "branch/path/to"
      },
      {
        "https://github.com/eclipse/che/blob/branch/path/to/.devfile.yaml",
        "eclipse",
        "che",
        "branch/path/to"
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

    GithubUrl githubUrl = githubUrlParser.parse(url, null);

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
    when(personalAccessTokenManager.get(any(Subject.class), eq(null), anyString(), eq(null)))
        .thenReturn(Optional.of(personalAccessToken));

    when(githubApiClient.isConnected(eq("https://github.com"))).thenReturn(true);
    when(githubApiClient.getPullRequest(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(pr);

    String url = "https://github.com/eclipse/che/pull/20189";
    GithubUrl githubUrl = githubUrlParser.parse(url, null);

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

    GithubUrl githubUrl = githubUrlParser.parseWithoutAuthentication(url, null);

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
    when(personalAccessTokenManager.get(any(Subject.class), eq(null), anyString(), eq(null)))
        .thenReturn(Optional.of(personalAccessToken));
    when(githubApiClient.isConnected(eq("https://github.com"))).thenReturn(true);
    when(githubApiClient.getPullRequest(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(githubPullRequest);

    String url = "https://github.com/eclipse/che/pull/11103";
    githubUrlParser.parse(url, null);
  }

  @Test
  public void shouldParseServerUr() throws Exception {
    // given
    String url = "https://github-server.com/user/repo";

    // when
    GithubUrl githubUrl = githubUrlParser.parse(url, null);

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
    githubUrlParser.parse(url, null);

    // then
    verify(personalAccessTokenManager, times(2))
        .get(any(Subject.class), eq(null), eq("https://github-server.com"), eq(null));
  }

  @Test
  public void shouldValidateOldVersionGitHubServerUrl() throws Exception {
    // given
    githubUrlParser = probingLoopbackParser();
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
    githubUrlParser = probingLoopbackParser();
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

  /**
   * An unconfigured URL is probed to find out whether it is a GitHub server, which must not become
   * a way of having the server reach whatever the caller names.
   */
  @Test
  public void shouldNotProbeAPrivateAddress() throws Exception {
    // when
    boolean valid = githubUrlParser.isValid("http://10.0.0.1/user/repo");

    // then
    assertFalse(valid);
    verify(githubApiClient, never()).getUser(anyString());
  }

  /**
   * A hostname an administrator has trusted is a GitHub Enterprise server the user asked to use, so
   * the parser may probe it even though it is not publicly routable.
   */
  @Test
  public void shouldProbeATrustedInternalGitHubServer() throws Exception {
    // given
    UrlTargetValidator.trustHost("localhost");
    stubFor(
        get(urlEqualTo("/api/v3/user"))
            .willReturn(
                aResponse()
                    .withStatus(HTTP_UNAUTHORIZED)
                    .withBody("{\"message\": \"Requires authentication\",\n}")));

    // when
    boolean valid = githubUrlParser.isValid(wireMockServer.url("/user/repo"));

    // then
    assertTrue(valid);
  }

  /** The wiremock server stands in for a GitHub server, but is only reachable over loopback. */
  private GithubURLParser probingLoopbackParser() {
    return new GithubURLParser(
        personalAccessTokenManager, devfileFilenamesProvider, githubApiClient, null, false) {
      @Override
      boolean canContact(String serverUrl) {
        return true;
      }
    };
  }
}
