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
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.api.factory.server.bitbucket.server.BitbucketPersonalAccessToken;
import org.eclipse.che.api.factory.server.bitbucket.server.BitbucketServerApiClient;
import org.eclipse.che.api.factory.server.bitbucket.server.BitbucketUser;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.security.oauth.OAuthAPI;
import org.eclipse.che.security.oauth1.BitbucketServerOAuthAuthenticator;
import org.eclipse.che.security.oauth1.NoopOAuthAuthenticator;
import org.eclipse.che.security.oauth1.OAuthAuthenticationException;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class HttpBitbucketServerApiClientTest {
  private final String AUTHORIZATION_TOKEN =
      "OAuth oauth_consumer_key=\"key123321\", oauth_nonce=\"nonce\","
          + " oauth_signature=\"signature\", "
          + "oauth_signature_method=\"RSA-SHA1\", oauth_timestamp=\"1609250025\", "
          + "oauth_token=\"token\", oauth_version=\"1.0\"";
  WireMockServer wireMockServer;
  WireMock wireMock;
  BitbucketServerApiClient bitbucketServer;

  @Mock OAuthAPI oAuthAPI;
  String apiEndpoint;

  @BeforeMethod
  void start() {
    oAuthAPI = mock(OAuthAPI.class);
    apiEndpoint = "apiEndpoint";
    wireMockServer =
        new WireMockServer(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort());
    wireMockServer.start();
    WireMock.configureFor("localhost", wireMockServer.port());
    wireMock = new WireMock("localhost", wireMockServer.port());
    bitbucketServer =
        new HttpBitbucketServerApiClient(
            wireMockServer.url("/"),
            new BitbucketServerOAuthAuthenticator("", "", "", "") {
              @Override
              public String computeAuthorizationHeader(
                  String userId, String requestMethod, String requestUrl)
                  throws OAuthAuthenticationException {
                return AUTHORIZATION_TOKEN;
              }
            },
            oAuthAPI,
            apiEndpoint);
    stubFor(
        get(urlEqualTo("/plugins/servlet/applinks/whoami"))
            .willReturn(aResponse().withBody("ksmster")));
  }

  @AfterMethod
  void stop() {
    wireMockServer.stop();
  }

  @Test
  public void testGetUser()
      throws ScmItemNotFoundException, ScmUnauthorizedException, ScmCommunicationException {
    stubFor(
        get(urlEqualTo("/rest/api/1.0/users/ksmster"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo(AUTHORIZATION_TOKEN))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("bitbucket/rest/api/1.0/users/ksmster/response.json")));

    stubFor(
        get(urlPathEqualTo("/rest/api/1.0/users"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo(AUTHORIZATION_TOKEN))
            .withQueryParam("start", equalTo("0"))
            .withQueryParam("limit", equalTo("25"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("bitbucket/rest/api/1.0/users/filtered/response.json")));

    BitbucketUser user = bitbucketServer.getUser();
    assertNotNull(user);
  }

  @Test
  public void testGetUsers()
      throws ScmCommunicationException, ScmBadRequestException, ScmUnauthorizedException {
    stubFor(
        get(urlPathEqualTo("/rest/api/1.0/users"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo(AUTHORIZATION_TOKEN))
            .withQueryParam("start", equalTo("0"))
            .withQueryParam("limit", equalTo("25"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("bitbucket/rest/api/1.0/users/response_s0_l25.json")));
    stubFor(
        get(urlPathEqualTo("/rest/api/1.0/users"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo(AUTHORIZATION_TOKEN))
            .withQueryParam("start", equalTo("3"))
            .withQueryParam("limit", equalTo("25"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("bitbucket/rest/api/1.0/users/response_s3_l25.json")));
    stubFor(
        get(urlPathEqualTo("/rest/api/1.0/users"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo(AUTHORIZATION_TOKEN))
            .withQueryParam("start", equalTo("6"))
            .withQueryParam("limit", equalTo("25"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("bitbucket/rest/api/1.0/users/response_s6_l25.json")));
    stubFor(
        get(urlPathEqualTo("/rest/api/1.0/users"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo(AUTHORIZATION_TOKEN))
            .withQueryParam("start", equalTo("9"))
            .withQueryParam("limit", equalTo("25"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("bitbucket/rest/api/1.0/users/response_s9_l25.json")));

    List<String> page =
        bitbucketServer.getUsers().stream()
            .map(BitbucketUser::getSlug)
            .collect(Collectors.toList());
    assertEquals(
        page,
        ImmutableList.of(
            "admin",
            "ksmster",
            "skabashn",
            "user1",
            "user2",
            "user3",
            "user4",
            "user5",
            "user6",
            "user7"));
  }

  @Test
  public void testGetUsersFiltered()
      throws ScmCommunicationException, ScmBadRequestException, ScmUnauthorizedException {
    stubFor(
        get(urlPathEqualTo("/rest/api/1.0/users"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo(AUTHORIZATION_TOKEN))
            .withQueryParam("start", equalTo("0"))
            .withQueryParam("limit", equalTo("25"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("bitbucket/rest/api/1.0/users/filtered/response.json")));

    List<String> page =
        bitbucketServer.getUsers("ksmster").stream()
            .map(BitbucketUser::getSlug)
            .collect(Collectors.toList());
    assertEquals(page, ImmutableList.of("admin", "ksmster"));
  }

  @Test
  public void testGetPersonalAccessTokens()
      throws ScmCommunicationException, ScmBadRequestException, ScmItemNotFoundException,
          ScmUnauthorizedException {
    stubFor(
        get(urlPathEqualTo("/rest/access-tokens/1.0/users/ksmster"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo(AUTHORIZATION_TOKEN))
            .withQueryParam("start", equalTo("0"))
            .withQueryParam("limit", equalTo("25"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("bitbucket/rest/access-tokens/1.0/users/ksmster/response.json")));

    stubFor(
        get(urlPathEqualTo("/rest/api/1.0/users"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo(AUTHORIZATION_TOKEN))
            .withQueryParam("start", equalTo("0"))
            .withQueryParam("limit", equalTo("25"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("bitbucket/rest/api/1.0/users/filtered/response.json")));

    List<String> page =
        bitbucketServer.getPersonalAccessTokens().stream()
            .map(BitbucketPersonalAccessToken::getName)
            .collect(Collectors.toList());
    assertEquals(page, ImmutableList.of("che", "t2"));
  }

  @Test
  public void shouldBeAbleToCreatePAT()
      throws ScmCommunicationException, ScmBadRequestException, ScmUnauthorizedException,
          ScmItemNotFoundException {

    // given
    stubFor(
        put(urlPathEqualTo("/rest/access-tokens/1.0/users/ksmster"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo(AUTHORIZATION_TOKEN))
            .withHeader(HttpHeaders.ACCEPT, equalTo(MediaType.APPLICATION_JSON))
            .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_JSON))
            .withHeader(HttpHeaders.CONTENT_LENGTH, equalTo("152"))
            .willReturn(
                ok().withBodyFile("bitbucket/rest/access-tokens/1.0/users/ksmster/newtoken.json")));
    stubFor(
        get(urlPathEqualTo("/rest/api/1.0/users"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo(AUTHORIZATION_TOKEN))
            .withQueryParam("start", equalTo("0"))
            .withQueryParam("limit", equalTo("25"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("bitbucket/rest/api/1.0/users/filtered/response.json")));

    // when
    BitbucketPersonalAccessToken result =
        bitbucketServer.createPersonalAccessTokens(
            "myToKen", ImmutableSet.of("PROJECT_WRITE", "REPO_WRITE"));
    // then
    assertNotNull(result);
    assertEquals(result.getToken(), "token");
  }

  @Test
  public void shouldBeAbleToDeletePAT()
      throws ScmCommunicationException, ScmUnauthorizedException, ScmItemNotFoundException {

    // given
    stubFor(
        delete(urlPathEqualTo("/rest/access-tokens/1.0/users/ksmster/5"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo(AUTHORIZATION_TOKEN))
            .withHeader(HttpHeaders.ACCEPT, equalTo(MediaType.APPLICATION_JSON))
            .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_JSON))
            .willReturn(aResponse().withStatus(204)));

    stubFor(
        get(urlPathEqualTo("/rest/api/1.0/users"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo(AUTHORIZATION_TOKEN))
            .withQueryParam("start", equalTo("0"))
            .withQueryParam("limit", equalTo("25"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("bitbucket/rest/api/1.0/users/filtered/response.json")));

    // when
    bitbucketServer.deletePersonalAccessTokens("5");
  }

  @Test
  public void shouldBeAbleToGetExistedPAT()
      throws ScmCommunicationException, ScmUnauthorizedException, ScmItemNotFoundException {

    // given
    stubFor(
        get(urlPathEqualTo("/rest/access-tokens/1.0/users/ksmster/5"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer token"))
            .withHeader(HttpHeaders.ACCEPT, equalTo(MediaType.APPLICATION_JSON))
            .willReturn(
                ok().withBodyFile("bitbucket/rest/access-tokens/1.0/users/ksmster/newtoken.json")));

    stubFor(
        get(urlPathEqualTo("/rest/api/1.0/users"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer token"))
            .withQueryParam("start", equalTo("0"))
            .withQueryParam("limit", equalTo("25"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("bitbucket/rest/api/1.0/users/filtered/response.json")));

    // when
    BitbucketPersonalAccessToken result = bitbucketServer.getPersonalAccessToken("5", "token");
    // then
    assertNotNull(result);
    assertEquals(result.getToken(), "token");
  }

  @Test(expectedExceptions = ScmItemNotFoundException.class)
  public void shouldBeAbleToThrowNotFoundOnGePAT()
      throws ScmCommunicationException, ScmUnauthorizedException, ScmItemNotFoundException {

    // given
    stubFor(
        get(urlPathEqualTo("/rest/access-tokens/1.0/users/ksmster/5"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo(AUTHORIZATION_TOKEN))
            .withHeader(HttpHeaders.ACCEPT, equalTo(MediaType.APPLICATION_JSON))
            .willReturn(notFound()));

    // when
    bitbucketServer.getPersonalAccessToken("5", "token");
  }

  @Test(expectedExceptions = ScmUnauthorizedException.class)
  public void shouldBeAbleToThrowScmUnauthorizedExceptionOnGetUser()
      throws ScmCommunicationException, ScmUnauthorizedException, ScmItemNotFoundException {
    // given
    stubFor(
        get(urlEqualTo("/plugins/servlet/applinks/whoami")).willReturn(aResponse().withBody("")));

    // when
    bitbucketServer.getUser();
  }

  @Test(expectedExceptions = ScmUnauthorizedException.class)
  public void shouldBeAbleToThrowScmUnauthorizedExceptionOnGetPAT()
      throws ScmCommunicationException, ScmUnauthorizedException, ScmItemNotFoundException {

    // given
    stubFor(
        get(urlPathEqualTo("/rest/access-tokens/1.0/users/ksmster/5"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer token"))
            .withHeader(HttpHeaders.ACCEPT, equalTo(MediaType.APPLICATION_JSON))
            .willReturn(unauthorized()));
    stubFor(
        get(urlPathEqualTo("/rest/api/1.0/users"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer token"))
            .withQueryParam("start", equalTo("0"))
            .withQueryParam("limit", equalTo("25"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("bitbucket/rest/api/1.0/users/filtered/response.json")));

    // when
    bitbucketServer.getPersonalAccessToken("5", "token");
  }

  @Test(
      expectedExceptions = ScmCommunicationException.class,
      expectedExceptionsMessageRegExp =
          "The fallback noop authenticator cannot be used for authentication. Make sure OAuth is properly configured.")
  public void shouldThrowScmCommunicationExceptionInNoOauthAuthenticator()
      throws ScmCommunicationException, ScmUnauthorizedException, ScmItemNotFoundException,
          ForbiddenException, ServerException, ConflictException, UnauthorizedException,
          NotFoundException, BadRequestException {

    // given
    when(oAuthAPI.getOrRefreshToken(eq("bitbucket-server"))).thenReturn(mock(OAuthToken.class));
    HttpBitbucketServerApiClient localServer =
        new HttpBitbucketServerApiClient(
            wireMockServer.url("/"), new NoopOAuthAuthenticator(), oAuthAPI, apiEndpoint);

    // when
    localServer.getUser();
  }

  @Test
  public void shouldGetOauth2Token()
      throws ScmItemNotFoundException, ScmUnauthorizedException, ScmCommunicationException,
          ForbiddenException, ServerException, ConflictException, UnauthorizedException,
          NotFoundException, BadRequestException {
    // given
    OAuthToken token = mock(OAuthToken.class);
    when(token.getToken()).thenReturn("token");
    when(oAuthAPI.getOrRefreshToken(eq("bitbucket-server"))).thenReturn(token);
    bitbucketServer =
        new HttpBitbucketServerApiClient(
            wireMockServer.url("/"), new NoopOAuthAuthenticator(), oAuthAPI, apiEndpoint);
    stubFor(
        get(urlEqualTo("/rest/api/1.0/users/ksmster"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer token"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("bitbucket/rest/api/1.0/users/ksmster/response.json")));

    stubFor(
        get(urlPathEqualTo("/rest/api/1.0/users"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer token"))
            .withQueryParam("start", equalTo("0"))
            .withQueryParam("limit", equalTo("25"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("bitbucket/rest/api/1.0/users/filtered/response.json")));

    // when
    bitbucketServer.getUser();

    // then
    verify(oAuthAPI, times(2)).getOrRefreshToken(eq("bitbucket-server"));
  }
}
