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
package org.eclipse.che.api.factory.server.gitlab;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class GitlabUrlParserTest {

  @Mock private DevfileFilenamesProvider devfileFilenamesProvider;

  /** Instance of component that will be tested. */
  private GitlabUrlParser gitlabUrlParser;

  private WireMockServer wireMockServer;
  private WireMock wireMock;

  @BeforeClass
  public void prepare() {
    wireMockServer =
        new WireMockServer(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort());
    wireMockServer.start();
    WireMock.configureFor("localhost", wireMockServer.port());
    wireMock = new WireMock("localhost", wireMockServer.port());
  }

  @BeforeMethod
  public void setUp() {
    gitlabUrlParser =
        new GitlabUrlParser(
            "https://gitlab1.com",
            devfileFilenamesProvider,
            mock(PersonalAccessTokenManager.class));
  }

  /** Check URLs are valid with regexp */
  @Test(dataProvider = "UrlsProvider")
  public void checkRegexp(String url) {
    assertTrue(gitlabUrlParser.isValid(url), "url " + url + " is invalid");
  }

  @Test
  public void shouldParseWithBranch() throws ApiException {
    GitlabUrl gitlabUrl =
        gitlabUrlParser.parse("https://gitlab.com/user/project/test.git", "branch");
    assertEquals(gitlabUrl.getBranch(), "branch");
  }

  @Test
  public void shouldGetProviderUrlWithExtraSegment() throws ApiException {
    gitlabUrlParser =
        new GitlabUrlParser(
            "https://gitlab-server.com/scm",
            devfileFilenamesProvider,
            mock(PersonalAccessTokenManager.class));
    GitlabUrl gitlabUrl =
        gitlabUrlParser.parse("https://gitlab-server.com/scm/user/project/test.git", null);
    assertEquals(gitlabUrl.getProviderUrl(), "https://gitlab-server.com/scm");
  }

  @Test
  public void shouldGetProviderUrlWithExtraSegmentOnIpv6Endpoint() throws ApiException {
    gitlabUrlParser =
        new GitlabUrlParser(
            "https://[2001:db8::1]/scm",
            devfileFilenamesProvider,
            mock(PersonalAccessTokenManager.class));
    GitlabUrl gitlabUrl =
        gitlabUrlParser.parse("https://[2001:db8::1]/scm/user/project/test.git", null);
    assertEquals(gitlabUrl.getProviderUrl(), "https://[2001:db8::1]/scm");
  }

  @Test
  public void shouldGetProviderUrlWithExtraSegmentOnIpv6EndpointWithPort() throws ApiException {
    gitlabUrlParser =
        new GitlabUrlParser(
            "https://[2001:db8::1]:8443/scm",
            devfileFilenamesProvider,
            mock(PersonalAccessTokenManager.class));
    GitlabUrl gitlabUrl =
        gitlabUrlParser.parse("https://[2001:db8::1]:8443/scm/user/project/test.git", null);
    assertEquals(gitlabUrl.getProviderUrl(), "https://[2001:db8::1]:8443/scm");
  }

  @Test
  public void shouldParseIpv6UrlWithoutExtraSegment() throws ApiException {
    // given
    gitlabUrlParser =
        new GitlabUrlParser(
            "https://[2001:db8::1]",
            devfileFilenamesProvider,
            mock(PersonalAccessTokenManager.class));

    // when
    GitlabUrl gitlabUrl = gitlabUrlParser.parse("https://[2001:db8::1]/user/project.git", null);

    // then
    assertEquals(gitlabUrl.getProject(), "project");
    assertEquals(gitlabUrl.getSubGroups(), "user/project");
    assertEquals(gitlabUrl.getProviderUrl(), "https://[2001:db8::1]");
  }

  @Test
  public void shouldParseIpv6UrlWithBranch() throws ApiException {
    // given
    gitlabUrlParser =
        new GitlabUrlParser(
            "https://[2001:db8::1]",
            devfileFilenamesProvider,
            mock(PersonalAccessTokenManager.class));

    // when
    GitlabUrl gitlabUrl =
        gitlabUrlParser.parse("https://[2001:db8::1]/user/project/-/tree/feature-branch", null);

    // then
    assertEquals(gitlabUrl.getProject(), "project");
    assertEquals(gitlabUrl.getSubGroups(), "user/project");
    assertEquals(gitlabUrl.getBranch(), "feature-branch");
  }

  @Test
  public void shouldParseIpv6UrlWithBranchContainingSlash() throws ApiException {
    // given
    gitlabUrlParser =
        new GitlabUrlParser(
            "https://[2001:db8::1]",
            devfileFilenamesProvider,
            mock(PersonalAccessTokenManager.class));

    // when
    GitlabUrl gitlabUrl =
        gitlabUrlParser.parse("https://[2001:db8::1]/user/project/-/tree/feature/my-branch", null);

    // then
    assertEquals(gitlabUrl.getProject(), "project");
    assertEquals(gitlabUrl.getSubGroups(), "user/project");
    assertEquals(gitlabUrl.getBranch(), "feature/my-branch");
  }

  @Test
  public void shouldParseIpv6UrlWithRevisionParam() throws ApiException {
    // given
    gitlabUrlParser =
        new GitlabUrlParser(
            "https://[2001:db8::1]",
            devfileFilenamesProvider,
            mock(PersonalAccessTokenManager.class));

    // when
    GitlabUrl gitlabUrl =
        gitlabUrlParser.parse("https://[2001:db8::1]/user/project.git", "my-branch");

    // then
    assertEquals(gitlabUrl.getProject(), "project");
    assertEquals(gitlabUrl.getSubGroups(), "user/project");
    assertEquals(gitlabUrl.getBranch(), "my-branch");
  }

  @Test
  public void shouldParseIpv6UrlWithSubgroups() throws ApiException {
    // given
    gitlabUrlParser =
        new GitlabUrlParser(
            "https://[2001:db8::1]",
            devfileFilenamesProvider,
            mock(PersonalAccessTokenManager.class));

    // when
    GitlabUrl gitlabUrl =
        gitlabUrlParser.parse("https://[2001:db8::1]/group/subgroup/project.git", null);

    // then
    assertEquals(gitlabUrl.getProject(), "project");
    assertEquals(gitlabUrl.getSubGroups(), "group/subgroup/project");
  }

  @Test
  public void shouldParseIpv6LoopbackAddress() throws ApiException {
    // given
    gitlabUrlParser =
        new GitlabUrlParser(
            "https://[::1]", devfileFilenamesProvider, mock(PersonalAccessTokenManager.class));

    // when
    GitlabUrl gitlabUrl = gitlabUrlParser.parse("https://[::1]/user/project.git", null);

    // then
    assertEquals(gitlabUrl.getProject(), "project");
    assertEquals(gitlabUrl.getSubGroups(), "user/project");
    assertEquals(gitlabUrl.getProviderUrl(), "https://[::1]");
  }

  @Test
  public void shouldParseIpv6FullFormAddress() throws ApiException {
    // given
    gitlabUrlParser =
        new GitlabUrlParser(
            "https://[2001:0db8:0000:0000:0000:0000:0000:0001]",
            devfileFilenamesProvider,
            mock(PersonalAccessTokenManager.class));

    // when
    GitlabUrl gitlabUrl =
        gitlabUrlParser.parse(
            "https://[2001:0db8:0000:0000:0000:0000:0000:0001]/user/project.git", null);

    // then
    assertEquals(gitlabUrl.getProject(), "project");
    assertEquals(gitlabUrl.getSubGroups(), "user/project");
  }

  @Test
  public void shouldValidateIpv6Url() {
    // given
    gitlabUrlParser =
        new GitlabUrlParser(
            "https://[2001:db8::1]",
            devfileFilenamesProvider,
            mock(PersonalAccessTokenManager.class));

    // when/then
    assertTrue(gitlabUrlParser.isValid("https://[2001:db8::1]/user/project.git"));
  }

  @Test
  public void shouldValidateIpv6UrlWithPort() {
    // given
    gitlabUrlParser =
        new GitlabUrlParser(
            "https://[2001:db8::1]:8443",
            devfileFilenamesProvider,
            mock(PersonalAccessTokenManager.class));

    // when/then
    assertTrue(gitlabUrlParser.isValid("https://[2001:db8::1]:8443/user/project.git"));
  }

  @Test
  public void shouldValidateIpv6UrlWithBranch() {
    // given
    gitlabUrlParser =
        new GitlabUrlParser(
            "https://[2001:db8::1]",
            devfileFilenamesProvider,
            mock(PersonalAccessTokenManager.class));

    // when/then
    assertTrue(gitlabUrlParser.isValid("https://[2001:db8::1]/user/project/-/tree/master"));
  }

  @Test
  public void shouldParseIpv6UrlViaDynamicPatternMatching() throws ApiException {
    // The parser is configured for gitlab1.com (via setUp), but getPatternMatcherByUrl()
    // dynamically creates a pattern from the URL itself. This exercises the fixed
    // IPv6 bracket handling in getPatternMatcherByUrl() -- the code path where the
    // double-bracketing bug (Issue #4 in the verdict) was fixed.
    GitlabUrl gitlabUrl = gitlabUrlParser.parse("https://[2001:db8::1]/user/project.git", null);

    assertEquals(gitlabUrl.getProject(), "project");
    assertEquals(gitlabUrl.getSubGroups(), "user/project");
  }

  @Test
  public void shouldParseIpv6UrlViaDynamicPatternMatchingWithBranch() throws ApiException {
    // Exercises getPatternMatcherByUrl() dynamic path with IPv6 and branch
    GitlabUrl gitlabUrl =
        gitlabUrlParser.parse("https://[2001:db8::1]/user/project/-/tree/feature-branch", null);

    assertEquals(gitlabUrl.getProject(), "project");
    assertEquals(gitlabUrl.getSubGroups(), "user/project");
    assertEquals(gitlabUrl.getBranch(), "feature-branch");
  }

  @Test
  public void shouldParseWithUrlBranch() throws ApiException {
    GitlabUrl gitlabUrl =
        gitlabUrlParser.parse("https://gitlab.com/user/project/-/tree/master/", "branch");
    assertEquals(gitlabUrl.getBranch(), "master");
  }

  /** Compare parsing */
  @Test(dataProvider = "parsing")
  public void checkParsing(String url, String project, String subGroups, String branch) {
    GitlabUrl gitlabUrl = gitlabUrlParser.parse(url, null);

    assertEquals(gitlabUrl.getProject(), project);
    assertEquals(gitlabUrl.getSubGroups(), subGroups);
    assertEquals(gitlabUrl.getBranch(), branch);
  }

  /** Compare parsing */
  @Test(dataProvider = "parsing")
  public void shouldParseWithoutPredefinedEndpoint(
      String url, String project, String subGroups, String branch) {
    // given
    gitlabUrlParser =
        new GitlabUrlParser(null, devfileFilenamesProvider, mock(PersonalAccessTokenManager.class));
    // when
    GitlabUrl gitlabUrl = gitlabUrlParser.parse(url, null);

    // then
    assertEquals(gitlabUrl.getProject(), project);
    assertEquals(gitlabUrl.getSubGroups(), subGroups);
    assertEquals(gitlabUrl.getBranch(), branch);
  }

  @Test
  public void shouldValidateUrlByApiRequest() {
    // given
    String url = wireMockServer.url("/user/repo");
    stubFor(
        get(urlEqualTo("/oauth/token/info"))
            .willReturn(
                aResponse()
                    .withStatus(401)
                    .withBody(
                        "{\"error\":\"invalid_token\",\"error_description\":\"The access token is invalid\",\"state\":\"unauthorized\"}")));

    // when
    boolean result = gitlabUrlParser.isValid(url);

    // then
    assertTrue(result);
  }

  @Test
  public void shouldNotValidateUrlByApiRequestWithPlainStringResponse() {
    // given
    String url = wireMockServer.url("/user/repo");
    stubFor(
        get(urlEqualTo("/oauth/token/info"))
            .willReturn(aResponse().withStatus(401).withBody("plain string error")));

    // when
    boolean result = gitlabUrlParser.isValid(url);

    // then
    assertFalse(result);
  }

  @Test
  public void shouldNotValidateUrlByApiRequest() {
    // given
    String url = wireMockServer.url("/user/repo");
    stubFor(get(urlEqualTo("/oauth/token/info")).willReturn(aResponse().withStatus(500)));

    // when
    boolean result = gitlabUrlParser.isValid(url);

    // then
    assertFalse(result);
  }

  @DataProvider(name = "UrlsProvider")
  public Object[][] urls() {
    return new Object[][] {
      {"https://gitlab1.com/user/project/test1.git"},
      {"https://gitlab1.com/user/project1.git"},
      {"https://gitlab1.com/scm/project/test1.git"},
      {"https://gitlab1.com/user/project/"},
      {"https://gitlab1.com/user/project/repo/"},
      {"https://gitlab1.com/user/project/-/tree/master/"},
      {"https://gitlab1.com/user/project/repo/-/tree/master/subfolder"},
      {"git@gitlab1.com:user/project/test1.git"},
      {"git@gitlab1.com:user/project1.git"},
      {"git@gitlab1.com:scm/project/test1.git"},
      {"git@gitlab1.com:user/project/"},
      {"git@gitlab1.com:user/project/repo/"},
    };
  }

  @DataProvider(name = "parsing")
  public Object[][] expectedParsing() {
    return new Object[][] {
      {"https://gitlab1.com/user/project1.git", "project1", "user/project1", null},
      {"https://gitlab1.com/user/project/test1.git", "test1", "user/project/test1", null},
      {
        "https://gitlab1.com/user/project/group1/group2/test1.git",
        "test1",
        "user/project/group1/group2/test1",
        null
      },
      {"https://gitlab1.com/user/project/", "project", "user/project", null},
      {"https://gitlab1.com/user/project/repo/", "repo", "user/project/repo", null},
      {"git@gitlab1.com:user/project1.git", "project1", "user/project1", null},
      {"git@gitlab1.com:user/project/test1.git", "test1", "user/project/test1", null},
      {
        "git@gitlab1.com:user/project/group1/group2/test1.git",
        "test1",
        "user/project/group1/group2/test1",
        null
      },
      {"git@gitlab1.com:user/project/", "project", "user/project", null},
      {"git@gitlab1.com:user/project/repo/", "repo", "user/project/repo", null},
      {"https://gitlab1.com/user/project/-/tree/master/", "project", "user/project", "master"},
      {"https://gitlab1.com/user/project/repo/-/tree/foo", "repo", "user/project/repo", "foo"},
      {
        "https://gitlab1.com/user/project/repo/-/tree/branch/with/slash",
        "repo",
        "user/project/repo",
        "branch/with/slash"
      },
      {
        "https://gitlab1.com/user/project/group1/group2/repo/-/tree/foo/",
        "repo",
        "user/project/group1/group2/repo",
        "foo"
      }
    };
  }
}
