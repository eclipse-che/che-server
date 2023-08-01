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
package org.eclipse.che.api.factory.server.github;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.net.HttpURLConnection.HTTP_BAD_GATEWAY;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertEqualsNoOrder;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.google.common.net.HttpHeaders;
import java.lang.reflect.Field;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.commons.lang.Pair;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class GithubApiClientTest {

  private GithubApiClient client;
  WireMockServer wireMockServer;
  WireMock wireMock;

  @BeforeMethod
  void start() {
    wireMockServer =
        new WireMockServer(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort());
    wireMockServer.start();
    WireMock.configureFor("localhost", wireMockServer.port());
    wireMock = new WireMock("localhost", wireMockServer.port());
    client = new GithubApiClient(wireMockServer.url("/"));
  }

  @AfterMethod
  void stop() {
    wireMockServer.stop();
  }

  @Test
  public void shouldUseDefaultApiUrl() throws Exception {
    // given
    client = new GithubApiClient("https://github.com");
    Field serverUrl = client.getClass().getDeclaredField("apiServerUrl");
    serverUrl.setAccessible(true);
    // then
    assertEquals(serverUrl.get(client).toString(), "https://api.github.com/");
  }

  @Test
  public void shouldUseDefaultApiUrlWithNull() throws Exception {
    // given
    client = new GithubApiClient(null);
    Field serverUrl = client.getClass().getDeclaredField("apiServerUrl");
    serverUrl.setAccessible(true);
    // then
    assertEquals(serverUrl.get(client).toString(), "https://api.github.com/");
  }

  @Test
  public void shouldUseDefaultApiUrlWithEmpty() throws Exception {
    // given
    client = new GithubApiClient("");
    Field serverUrl = client.getClass().getDeclaredField("apiServerUrl");
    serverUrl.setAccessible(true);
    // then
    assertEquals(serverUrl.get(client).toString(), "https://api.github.com/");
  }

  @Test(expectedExceptions = ScmCommunicationException.class)
  public void shouldThrowExceptionOnUserParseError() throws Exception {
    // given
    stubFor(get(urlEqualTo("/api/v3/user")).willReturn(aResponse().withBody("invalid value")));
    // when
    client.getUser("token");
  }

  @Test
  public void testGetUser() throws Exception {
    stubFor(
        get(urlEqualTo("/api/v3/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token token1"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("github/rest/user/response.json")));

    GithubUser user = client.getUser("token1");
    assertNotNull(user, "GitHub API should have returned a non-null user object");
    assertEquals(user.getId(), 123456789, "GitHub user id was not parsed properly by client");
    assertEquals(
        user.getLogin(), "github-user", "GitHub user login was not parsed properly by client");
    assertEquals(
        user.getEmail(),
        "github-user@acme.com",
        "GitHub user email was not parsed properly by client");
    assertEquals(
        user.getName(), "Github User", "GitHub user name was not parsed properly by client");
  }

  @Test
  public void testGetPullRequest() throws Exception {
    // given
    stubFor(
        get(urlEqualTo("/api/v3/repos/user/repo/pulls/id"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token token1"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("github/rest/pullRequest/response.json")));

    // when
    GithubPullRequest pullRequest = client.getPullRequest("id", "user", "repo", "token1");
    // then
    assertNotNull(pullRequest);
    assertNotNull(pullRequest.getHead());
    assertEquals(pullRequest.getState(), "open");
  }

  @Test(expectedExceptions = ScmCommunicationException.class)
  public void shouldThrowExceptionOnPullRequestParseError() throws Exception {
    // given
    stubFor(
        get(urlEqualTo("/api/v3/repos/user/repo/pulls/id"))
            .willReturn(aResponse().withBody("invalid value")));
    // when
    client.getPullRequest("id", "user", "repo", "token1");
  }

  @Test
  public void testGetTokenScopes() throws Exception {
    stubFor(
        get(urlEqualTo("/api/v3/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token token1"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader(GithubApiClient.GITHUB_OAUTH_SCOPES_HEADER, "repo, user:email")
                    .withBodyFile("github/rest/user/response.json")));

    Pair<String, String[]> pair = client.getTokenScopes("token1");
    String[] scopes = pair.second;
    String[] expectedScopes = {"repo", "user:email"};
    assertNotNull(scopes, "GitHub API should have returned a non-null scope array");
    assertEqualsNoOrder(
        scopes, expectedScopes, "Returned scope array does not match expected values");
    assertEquals(pair.first, "github-user");
  }

  @Test
  public void testGetTokenScopesWithNoScopeHeader() throws Exception {
    stubFor(
        get(urlEqualTo("/api/v3/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token token1"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("github/rest/user/response.json")));

    String[] scopes = client.getTokenScopes("token1").second;
    assertNotNull(scopes, "GitHub API should have returned a non-null scope array");
    assertEquals(
        scopes.length,
        0,
        "A response with no "
            + GithubApiClient.GITHUB_OAUTH_SCOPES_HEADER
            + " header should return an empty array");
  }

  @Test
  public void testGetTokenScopesWithNoScope() throws Exception {
    stubFor(
        get(urlEqualTo("/api/v3/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token token1"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader(GithubApiClient.GITHUB_OAUTH_SCOPES_HEADER, "")
                    .withBodyFile("github/rest/user/response.json")));

    String[] scopes = client.getTokenScopes("token1").second;
    assertNotNull(scopes, "GitHub API should have returned a non-null scope array");
    assertEquals(
        scopes.length,
        0,
        "A response with empty "
            + GithubApiClient.GITHUB_OAUTH_SCOPES_HEADER
            + " header should return an empty array");
  }

  @Test
  public void shouldReturnFalseOnConnectedToOtherHost() {
    assertFalse(client.isConnected("https://other.com"));
  }

  @Test
  public void shouldReturnTrueWhenConnectedToGithub() {
    assertTrue(client.isConnected(wireMockServer.url("/")));
  }

  @Test()
  public void shouldReturnNull() throws Exception {
    // given
    stubFor(get(urlEqualTo("/api/v3/user")).willReturn(aResponse().withStatus(HTTP_NO_CONTENT)));
    // when
    GithubUser user = client.getUser("token");
    // then
    assertNull(user);
  }

  @Test(
      expectedExceptions = ScmBadRequestException.class,
      expectedExceptionsMessageRegExp = "bad request")
  public void shouldThrowExceptionOnBadRequestError() throws Exception {
    // given
    stubFor(
        get(urlEqualTo("/api/v3/user"))
            .willReturn(aResponse().withStatus(HTTP_BAD_REQUEST).withBody("bad request")));
    // when
    client.getUser("token");
  }

  @Test(
      expectedExceptions = ScmItemNotFoundException.class,
      expectedExceptionsMessageRegExp = "item not found")
  public void shouldThrowExceptionOnNotFoundError() throws Exception {
    // given
    stubFor(
        get(urlEqualTo("/api/v3/user"))
            .willReturn(aResponse().withStatus(HTTP_NOT_FOUND).withBody("item not found")));
    // when
    client.getUser("token");
  }

  @Test(
      expectedExceptions = ScmCommunicationException.class,
      expectedExceptionsMessageRegExp =
          "Unexpected status code 502 \\(GET http://localhost:\\d*/api/v3/user\\) 502")
  public void shouldThrowExceptionOnOtherError() throws Exception {
    // given
    stubFor(
        get(urlEqualTo("/api/v3/user"))
            .willReturn(aResponse().withStatus(HTTP_BAD_GATEWAY).withBody("item not found")));
    // when
    client.getUser("token");
  }

  @Test
  public void testGetLatestCommit() throws Exception {
    stubFor(
        get(urlEqualTo("/api/v3/repos/eclipse-che/che-server/commits?sha=HEAD&page=1&per_page=1"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token token1"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBody("[{\"sha\": \"test_sha\", \"url\": \"http://commit.url\" }]")));

    GithubCommit commit = client.getLatestCommit("eclipse-che", "che-server", "HEAD", "token1");

    assertNotNull(commit, "GitHub API should return the latest commit");
    assertEquals(commit.getSha(), "test_sha");
    assertEquals(commit.getUrl(), "http://commit.url");
  }
}
