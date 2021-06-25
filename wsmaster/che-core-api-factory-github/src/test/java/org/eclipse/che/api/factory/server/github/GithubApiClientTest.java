/*
 * Copyright (c) 2012-2021 Red Hat, Inc.
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
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertEqualsNoOrder;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.google.common.net.HttpHeaders;
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
  public void testGetUser() throws Exception {
    stubFor(
        get(urlEqualTo("/user"))
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
  public void testGetTokenScopes() throws Exception {
    stubFor(
        get(urlEqualTo("/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token token1"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader(GithubApiClient.GITHUB_OAUTH_SCOPES_HEADER, "repo, user:email")
                    .withBodyFile("github/rest/user/response.json")));

    String[] scopes = client.getTokenScopes("token1");
    String[] expectedScopes = {"repo", "user:email"};
    assertNotNull(scopes, "GitHub API should have returned a non-null scope array");
    assertEqualsNoOrder(
        scopes, expectedScopes, "Returned scope array does not match expected values");
  }

  @Test
  public void testGetTokenScopesWithNoScopeHeader() throws Exception {
    stubFor(
        get(urlEqualTo("/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token token1"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("github/rest/user/response.json")));

    String[] scopes = client.getTokenScopes("token1");
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
        get(urlEqualTo("/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token token1"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader(GithubApiClient.GITHUB_OAUTH_SCOPES_HEADER, "")
                    .withBodyFile("github/rest/user/response.json")));

    String[] scopes = client.getTokenScopes("token1");
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
    assertTrue(client.isConnected("https://github.com"));
  }
}
