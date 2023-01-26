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
public class BitbucketServerURLParserTest {

  @Mock private DevfileFilenamesProvider devfileFilenamesProvider;

  /** Instance of component that will be tested. */
  private BitbucketServerURLParser bitbucketURLParser;

  private WireMockServer wireMockServer;

  @BeforeClass
  public void prepare() {
    wireMockServer =
        new WireMockServer(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort());
    wireMockServer.start();
    WireMock.configureFor("localhost", wireMockServer.port());
    new WireMock("localhost", wireMockServer.port());
  }

  @BeforeMethod
  public void setUp() {
    bitbucketURLParser =
        new BitbucketServerURLParser(
            "https://bitbucket.2mcl.com,https://bbkt.com",
            devfileFilenamesProvider,
            mock(PersonalAccessTokenManager.class));
  }

  /** Check URLs are valid with regexp */
  @Test(dataProvider = "UrlsProvider")
  public void checkRegexp(String url) {
    assertTrue(bitbucketURLParser.isValid(url), "url " + url + " is invalid");
  }

  /** Compare parsing */
  @Test(dataProvider = "parsing")
  public void checkParsing(String url, String project, String repository, String branch) {
    BitbucketServerUrl bitbucketServerUrl = bitbucketURLParser.parse(url);

    assertEquals(bitbucketServerUrl.getProject(), project);
    assertEquals(bitbucketServerUrl.getRepository(), repository);
    assertEquals(bitbucketServerUrl.getBranch(), branch);
  }

  @Test(
      expectedExceptions = IllegalArgumentException.class,
      expectedExceptionsMessageRegExp =
          "The given url https://github.com/org/repo is not a valid Bitbucket server URL. Check either URL or server configuration.")
  public void shouldThrowExceptionWhenURLDintMatchAnyConfiguredServer() {
    bitbucketURLParser.parse("https://github.com/org/repo");
  }

  @Test
  public void shouldValidateUrlByApiRequest() {
    // given
    bitbucketURLParser =
        new BitbucketServerURLParser(
            null, devfileFilenamesProvider, mock(PersonalAccessTokenManager.class));
    String url = wireMockServer.url("/users/user/repos/repo");
    stubFor(
        get(urlEqualTo("/rest/access-tokens/1.0/users/0")).willReturn(aResponse().withStatus(401)));

    // when
    boolean result = bitbucketURLParser.isValid(url);

    // then
    assertTrue(result);
  }

  @Test
  public void shouldNotValidateUrlByApiRequest() {
    // given
    String url = wireMockServer.url("/users/user/repos/repo");
    stubFor(
        get(urlEqualTo("/rest/access-tokens/1.0/users/0")).willReturn(aResponse().withStatus(500)));

    // when
    boolean result = bitbucketURLParser.isValid(url);

    // then
    assertFalse(result);
  }

  @DataProvider(name = "UrlsProvider")
  public Object[][] urls() {
    return new Object[][] {
      {"https://bitbucket.2mcl.com/scm/project/test1.git"},
      {"https://bitbucket.2mcl.com/projects/project/repos/test1/browse?at=refs%2Fheads%2Fbranch"},
      {"https://bbkt.com/scm/project/test1.git"},
    };
  }

  @DataProvider(name = "parsing")
  public Object[][] expectedParsing() {
    return new Object[][] {
      {"https://bitbucket.2mcl.com/scm/project/test1.git", "project", "test1", null},
      {
        "https://bitbucket.2mcl.com/projects/project/repos/test1/browse?at=refs%2Fheads%2Fbranch",
        "project",
        "test1",
        "refs%2Fheads%2Fbranch"
      },
      {
        "https://bbkt.com/projects/project/repos/test1/browse?at=refs%2Fheads%2Fbranch",
        "project",
        "test1",
        "refs%2Fheads%2Fbranch"
      }
    };
  }
}
