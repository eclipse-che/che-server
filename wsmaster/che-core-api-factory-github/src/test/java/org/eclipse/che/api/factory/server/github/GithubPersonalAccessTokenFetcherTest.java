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

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.testng.Assert.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.Optional;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.security.oauth.OAuthAPI;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class GithubPersonalAccessTokenFetcherTest {

  @Mock OAuthAPI oAuthAPI;
  GithubPersonalAccessTokenFetcher githubPATFetcher;

  final int httpPort = 3301;
  WireMockServer wireMockServer;
  WireMock wireMock;

  @BeforeMethod
  void start() {

    wireMockServer =
        new WireMockServer(wireMockConfig().notifier(new Slf4jNotifier(false)).port(httpPort));
    wireMockServer.start();
    WireMock.configureFor("localhost", httpPort);
    wireMock = new WireMock("localhost", httpPort);
    githubPATFetcher = new GithubPersonalAccessTokenFetcher("http://che.api", oAuthAPI);
  }

  @AfterMethod
  void stop() {
    wireMockServer.stop();
  }

  @Test
  public void shouldValidateSCMServerWithTrailingSlash() throws Exception {
    PersonalAccessToken personalAccessToken =
        new PersonalAccessToken(
            "https://github.com/",
            "cheUserId",
            "scmUserName",
            "scmUserId",
            "scmTokenName",
            "scmTokenId",
            "token");
    assertEquals(
        githubPATFetcher.isValid(personalAccessToken),
        Optional.of(Boolean.TRUE),
        "Should validate SCM server even with trailing /");
  }

  @Test
  public void testContainsScope() {
    String[] tokenScopes = {"repo", "notifications", "write:org", "admin:gpg_key"};
    assertTrue(
        githubPATFetcher.containsScopes(ImmutableSet.of("repo"), tokenScopes),
        "'repo' scope should have matched directly.");
    assertTrue(
        githubPATFetcher.containsScopes(ImmutableSet.of("public_repo"), tokenScopes),
        "'public_repo' scope should have matched since token has parent scope 'repo'.");
    assertTrue(
        githubPATFetcher.containsScopes(
            ImmutableSet.of("read:gpg_key", "write:gpg_key"), tokenScopes),
        "'admin:gpg_key' token scope should cover both scope requirement.");
    assertFalse(
        githubPATFetcher.containsScopes(ImmutableSet.of("admin:org"), tokenScopes),
        "'admin:org' scope should not match since token only has scope 'write:org'.");
    assertFalse(
        githubPATFetcher.containsScopes(ImmutableSet.of("gist"), tokenScopes),
        "'gist' shouldn't matche since it is not present in token scope");
    assertTrue(
        githubPATFetcher.containsScopes(ImmutableSet.of("unknown", "repo"), tokenScopes),
        "'unknown' is not even a valid GitHub scope, so it shouldn't have any impact.");
    assertTrue(
        githubPATFetcher.containsScopes(Collections.emptySet(), tokenScopes),
        "No required scope should always return true");
    assertFalse(
        githubPATFetcher.containsScopes(ImmutableSet.of("repo"), new String[0]),
        "Token has no scope, so it should not match");
    assertTrue(
        githubPATFetcher.containsScopes(Collections.emptySet(), new String[0]),
        "No scope requirement and a token with no scope should match");
  }

  // TODO: should those tests be implemented?  Any value added?
  /*@Test(
      expectedExceptions = ScmCommunicationException.class,
      expectedExceptionsMessageRegExp =
          "Current token doesn't have the necessary  privileges. Please make sure Che app scopes are correct and containing at least: \\[api, write_repository, openid\\]")
  public void shouldThrowExceptionOnInsufficientTokenScopes() throws Exception {
    Subject subject = new SubjectImpl("Username", "id1", "token", false);
    OAuthToken oAuthToken = newDto(OAuthToken.class).withToken("oauthtoken").withScope("api repo");
    when(oAuthAPI.getToken(anyString())).thenReturn(oAuthToken);

    stubFor(
        get(urlEqualTo("/api/v4/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer oauthtoken"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("gitlab/rest/api/v4/user/response.json")));

    stubFor(
        get(urlEqualTo("/oauth/token/info"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer oauthtoken"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("gitlab/rest/api/v4/user/token_info_lack_scopes.json")));

    oAuthTokenFetcher.fetchPersonalAccessToken(subject, wireMockServer.url("/"));
  }

  @Test(
      expectedExceptions = ScmUnauthorizedException.class,
      expectedExceptionsMessageRegExp = "Username is not authorized in gitlab OAuth provider.")
  public void shouldThrowUnauthorizedExceptionWhenUserNotLoggedIn() throws Exception {
    Subject subject = new SubjectImpl("Username", "id1", "token", false);
    when(oAuthAPI.getToken(anyString())).thenThrow(UnauthorizedException.class);

    oAuthTokenFetcher.fetchPersonalAccessToken(subject, wireMockServer.url("/"));
  }

  @Test
  public void shouldReturnToken() throws Exception {
    Subject subject = new SubjectImpl("Username", "id1", "token", false);
    OAuthToken oAuthToken =
        newDto(OAuthToken.class).withToken("oauthtoken").withScope("api write_repository openid");
    when(oAuthAPI.getToken(anyString())).thenReturn(oAuthToken);

    stubFor(
        get(urlEqualTo("/oauth/token/info"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer oauthtoken"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("gitlab/rest/api/v4/user/token_info.json")));

    stubFor(
        get(urlEqualTo("/api/v4/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer oauthtoken"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("gitlab/rest/api/v4/user/response.json")));

    PersonalAccessToken token =
        oAuthTokenFetcher.fetchPersonalAccessToken(subject, wireMockServer.url("/"));
    assertNotNull(token);
  }*/
}
