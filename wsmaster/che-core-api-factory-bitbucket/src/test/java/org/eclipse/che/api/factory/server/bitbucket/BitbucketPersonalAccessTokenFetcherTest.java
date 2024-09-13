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
package org.eclipse.che.api.factory.server.bitbucket;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static org.eclipse.che.api.factory.server.scm.PersonalAccessTokenFetcher.OAUTH_2_PREFIX;
import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.google.common.net.HttpHeaders;
import java.util.Optional;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenParams;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.commons.lang.Pair;
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
public class BitbucketPersonalAccessTokenFetcherTest {

  @Mock OAuthAPI oAuthAPI;
  BitbucketPersonalAccessTokenFetcher bitbucketPersonalAccessTokenFetcher;

  final int httpPort = 3301;
  WireMockServer wireMockServer;
  WireMock wireMock;

  final String bitbucketOauthToken = "token";

  @BeforeMethod
  void start() {

    wireMockServer =
        new WireMockServer(wireMockConfig().notifier(new Slf4jNotifier(false)).port(httpPort));
    wireMockServer.start();
    WireMock.configureFor("localhost", httpPort);
    wireMock = new WireMock("localhost", httpPort);
    bitbucketPersonalAccessTokenFetcher =
        new BitbucketPersonalAccessTokenFetcher(
            "http://che.api", oAuthAPI, new BitbucketApiClient(wireMockServer.url("/")));
  }

  @AfterMethod
  void stop() {
    wireMockServer.stop();
  }

  @Test
  public void shouldNotValidateSCMServerWithTrailingSlash() throws Exception {
    stubFor(
        get(urlEqualTo("/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + bitbucketOauthToken))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("bitbucket/rest/user/response.json")));
    PersonalAccessTokenParams personalAccessTokenParams =
        new PersonalAccessTokenParams(
            "https://bitbucket.org/",
            "provider",
            "scmTokenName",
            "scmTokenId",
            bitbucketOauthToken,
            null);
    assertTrue(
        bitbucketPersonalAccessTokenFetcher.isValid(personalAccessTokenParams).isEmpty(),
        "Should not validate SCM server with trailing /");
  }

  @Test(
      expectedExceptions = ScmCommunicationException.class,
      expectedExceptionsMessageRegExp =
          "Current token doesn't have the necessary privileges. Please make sure Che app scopes are correct and containing at least: repository:write and account")
  public void shouldThrowExceptionOnInsufficientTokenScopes() throws Exception {
    Subject subject = new SubjectImpl("Username", "id1", "token", false);
    OAuthToken oAuthToken = newDto(OAuthToken.class).withToken(bitbucketOauthToken).withScope("");
    when(oAuthAPI.getOrRefreshToken(anyString())).thenReturn(oAuthToken);

    stubFor(
        get(urlEqualTo("/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + bitbucketOauthToken))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader(BitbucketApiClient.BITBUCKET_OAUTH_SCOPES_HEADER, "")
                    .withBodyFile("bitbucket/rest/user/response.json")));

    bitbucketPersonalAccessTokenFetcher.fetchPersonalAccessToken(
        subject, BitbucketApiClient.BITBUCKET_SERVER);
  }

  @Test(
      expectedExceptions = ScmUnauthorizedException.class,
      expectedExceptionsMessageRegExp = "Username is not authorized in bitbucket OAuth provider.")
  public void shouldThrowUnauthorizedExceptionWhenUserNotLoggedIn() throws Exception {
    Subject subject = new SubjectImpl("Username", "id1", "token", false);
    when(oAuthAPI.getOrRefreshToken(anyString())).thenThrow(UnauthorizedException.class);

    bitbucketPersonalAccessTokenFetcher.fetchPersonalAccessToken(
        subject, BitbucketApiClient.BITBUCKET_SERVER);
  }

  @Test
  public void shouldReturnToken() throws Exception {
    Subject subject = new SubjectImpl("Username", "id1", "token", false);
    OAuthToken oAuthToken =
        newDto(OAuthToken.class).withToken(bitbucketOauthToken).withScope("repo");
    when(oAuthAPI.getOrRefreshToken(anyString())).thenReturn(oAuthToken);

    stubFor(
        get(urlEqualTo("/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + bitbucketOauthToken))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader(
                        BitbucketApiClient.BITBUCKET_OAUTH_SCOPES_HEADER,
                        "repository:write,account")
                    .withBodyFile("bitbucket/rest/user/response.json")));

    PersonalAccessToken token =
        bitbucketPersonalAccessTokenFetcher.fetchPersonalAccessToken(
            subject, BitbucketApiClient.BITBUCKET_SERVER);
    assertNotNull(token);
  }

  @Test
  public void shouldValidatePersonalToken() throws Exception {
    stubFor(
        get(urlEqualTo("/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + bitbucketOauthToken))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader(
                        BitbucketApiClient.BITBUCKET_OAUTH_SCOPES_HEADER,
                        "repository:write,account")
                    .withBodyFile("bitbucket/rest/user/response.json")));

    PersonalAccessTokenParams params =
        new PersonalAccessTokenParams(
            "https://bitbucket.org",
            "provider",
            "params-name",
            "tid-23434",
            bitbucketOauthToken,
            null);

    Optional<Pair<Boolean, String>> valid = bitbucketPersonalAccessTokenFetcher.isValid(params);
    assertTrue(valid.isPresent());
    assertTrue(valid.get().first);
  }

  @Test
  public void shouldValidateOauthToken() throws Exception {
    stubFor(
        get(urlEqualTo("/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + bitbucketOauthToken))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader(
                        BitbucketApiClient.BITBUCKET_OAUTH_SCOPES_HEADER,
                        "repository:write,account")
                    .withBodyFile("bitbucket/rest/user/response.json")));

    PersonalAccessTokenParams params =
        new PersonalAccessTokenParams(
            "https://bitbucket.org",
            "provider",
            OAUTH_2_PREFIX + "-params-name",
            "tid-23434",
            bitbucketOauthToken,
            null);

    Optional<Pair<Boolean, String>> valid = bitbucketPersonalAccessTokenFetcher.isValid(params);
    assertTrue(valid.isPresent());
    assertTrue(valid.get().first);
  }

  @Test
  public void shouldNotValidateExpiredOauthToken() throws Exception {
    stubFor(get(urlEqualTo("/user")).willReturn(aResponse().withStatus(HTTP_UNAUTHORIZED)));

    PersonalAccessTokenParams params =
        new PersonalAccessTokenParams(
            "https://bitbucket.org",
            "provider",
            OAUTH_2_PREFIX + "-token-name",
            "tid-23434",
            bitbucketOauthToken,
            null);

    assertFalse(bitbucketPersonalAccessTokenFetcher.isValid(params).isPresent());
  }

  @Test(
      expectedExceptions = ScmUnauthorizedException.class,
      expectedExceptionsMessageRegExp = "Username is not authorized in bitbucket OAuth provider.")
  public void shouldThrowUnauthorizedExceptionIfTokenIsNotValid() throws Exception {
    Subject subject = new SubjectImpl("Username", "id1", "token", false);
    OAuthToken oAuthToken = newDto(OAuthToken.class).withToken(bitbucketOauthToken).withScope("");
    when(oAuthAPI.getOrRefreshToken(anyString())).thenReturn(oAuthToken);
    stubFor(
        get(urlEqualTo("/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token " + bitbucketOauthToken))
            .willReturn(aResponse().withStatus(HTTP_UNAUTHORIZED)));

    bitbucketPersonalAccessTokenFetcher.fetchPersonalAccessToken(
        subject, BitbucketApiClient.BITBUCKET_SERVER);
  }
}
