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
import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.HttpHeaders;
import java.util.Collections;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.commons.subject.SubjectImpl;
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

  final String githubOauthToken = "gho_token1";

  @BeforeMethod
  void start() {

    wireMockServer =
        new WireMockServer(wireMockConfig().notifier(new Slf4jNotifier(false)).port(httpPort));
    wireMockServer.start();
    WireMock.configureFor("localhost", httpPort);
    wireMock = new WireMock("localhost", httpPort);
    githubPATFetcher =
        new GithubPersonalAccessTokenFetcher(
            "http://che.api", oAuthAPI, new GithubApiClient(wireMockServer.url("/")));
  }

  @AfterMethod
  void stop() {
    wireMockServer.stop();
  }

  @Test
  public void shouldNotValidateSCMServerWithTrailingSlash() throws Exception {
    stubFor(
        get(urlEqualTo("/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token " + githubOauthToken))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader(GithubApiClient.GITHUB_OAUTH_SCOPES_HEADER, "repo")
                    .withBodyFile("github/rest/user/response.json")));
    PersonalAccessToken personalAccessToken =
        new PersonalAccessToken(
            "https://github.com/",
            "cheUserId",
            "scmUserName",
            "scmUserId",
            "scmTokenName",
            "scmTokenId",
            githubOauthToken);
    assertTrue(
        githubPATFetcher.isValid(personalAccessToken).isEmpty(),
        "Should not validate SCM server with trailing /");
  }

  @Test
  public void testContainsScope() {
    String[] tokenScopes = {"repo", "notifications", "write:org", "admin:gpg_key"};
    assertTrue(
        githubPATFetcher.containsScopes(tokenScopes, ImmutableSet.of("repo")),
        "'repo' scope should have matched directly.");
    assertTrue(
        githubPATFetcher.containsScopes(tokenScopes, ImmutableSet.of("public_repo")),
        "'public_repo' scope should have matched since token has parent scope 'repo'.");
    assertTrue(
        githubPATFetcher.containsScopes(
            tokenScopes, ImmutableSet.of("read:gpg_key", "write:gpg_key")),
        "'admin:gpg_key' token scope should cover both scope requirement.");
    assertFalse(
        githubPATFetcher.containsScopes(tokenScopes, ImmutableSet.of("admin:org")),
        "'admin:org' scope should not match since token only has scope 'write:org'.");
    assertFalse(
        githubPATFetcher.containsScopes(tokenScopes, ImmutableSet.of("gist")),
        "'gist' shouldn't matche since it is not present in token scope");
    assertTrue(
        githubPATFetcher.containsScopes(tokenScopes, ImmutableSet.of("unknown", "repo")),
        "'unknown' is not even a valid GitHub scope, so it shouldn't have any impact.");
    assertTrue(
        githubPATFetcher.containsScopes(tokenScopes, Collections.emptySet()),
        "No required scope should always return true");
    assertFalse(
        githubPATFetcher.containsScopes(new String[0], ImmutableSet.of("repo")),
        "Token has no scope, so it should not match");
    assertTrue(
        githubPATFetcher.containsScopes(new String[0], Collections.emptySet()),
        "No scope requirement and a token with no scope should match");
  }

  @Test(
      expectedExceptions = ScmCommunicationException.class,
      expectedExceptionsMessageRegExp =
          "Current token doesn't have the necessary privileges. Please make sure Che app scopes are correct and containing at least: \\[repo\\]")
  public void shouldThrowExceptionOnInsufficientTokenScopes() throws Exception {
    Subject subject = new SubjectImpl("Username", "id1", "token", false);
    OAuthToken oAuthToken = newDto(OAuthToken.class).withToken(githubOauthToken).withScope("");
    when(oAuthAPI.getToken(anyString())).thenReturn(oAuthToken);

    stubFor(
        get(urlEqualTo("/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token " + githubOauthToken))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader(GithubApiClient.GITHUB_OAUTH_SCOPES_HEADER, "")
                    .withBodyFile("github/rest/user/response.json")));

    githubPATFetcher.fetchPersonalAccessToken(subject, GithubApiClient.GITHUB_SERVER);
  }

  @Test(
      expectedExceptions = ScmUnauthorizedException.class,
      expectedExceptionsMessageRegExp = "Username is not authorized in github OAuth provider.")
  public void shouldThrowUnauthorizedExceptionWhenUserNotLoggedIn() throws Exception {
    Subject subject = new SubjectImpl("Username", "id1", "token", false);
    when(oAuthAPI.getToken(anyString())).thenThrow(UnauthorizedException.class);

    githubPATFetcher.fetchPersonalAccessToken(subject, GithubApiClient.GITHUB_SERVER);
  }

  @Test
  public void shouldReturnToken() throws Exception {
    Subject subject = new SubjectImpl("Username", "id1", "token", false);
    OAuthToken oAuthToken = newDto(OAuthToken.class).withToken(githubOauthToken).withScope("repo");
    when(oAuthAPI.getToken(anyString())).thenReturn(oAuthToken);

    stubFor(
        get(urlEqualTo("/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token " + githubOauthToken))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader(GithubApiClient.GITHUB_OAUTH_SCOPES_HEADER, "repo")
                    .withBodyFile("github/rest/user/response.json")));

    PersonalAccessToken token =
        githubPATFetcher.fetchPersonalAccessToken(subject, GithubApiClient.GITHUB_SERVER);
    assertNotNull(token);
  }

  @Test
  public void shouldValidatePersonalToken() throws Exception {
    stubFor(
        get(urlEqualTo("/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token " + githubOauthToken))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader(GithubApiClient.GITHUB_OAUTH_SCOPES_HEADER, "repo")
                    .withBodyFile("github/rest/user/response.json")));

    PersonalAccessToken token =
        new PersonalAccessToken(
            "https://github.com",
            "cheUser",
            "username",
            "123456789",
            "token-name",
            "tid-23434",
            githubOauthToken);

    assertTrue(githubPATFetcher.isValid(token).get());
  }
}
