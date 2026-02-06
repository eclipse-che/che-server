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
package org.eclipse.che.api.factory.server.azure.devops;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static org.eclipse.che.api.factory.server.scm.PersonalAccessTokenFetcher.OAUTH_2_PREFIX;
import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.google.common.net.HttpHeaders;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenParams;
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

/**
 * @author Anatalii Bazko
 */
@Listeners(MockitoTestNGListener.class)
public class AzureDevOpsPersonalAccessTokenFetcherTest {

  @Mock private AzureDevOpsApiClient azureDevOpsApiClient;
  @Mock private OAuthAPI oAuthAPI;
  @Mock private OAuthToken oAuthToken;
  @Mock private AzureDevOpsUser azureDevOpsUser;

  private final String azureDevOpsToken = "token";

  final int httpPort = 3301;
  WireMockServer wireMockServer;
  WireMock wireMock;

  final String azureOauthToken = "token";
  private AzureDevOpsPersonalAccessTokenFetcher personalAccessTokenFetcher;

  @BeforeMethod
  protected void start() {
    wireMockServer =
        new WireMockServer(wireMockConfig().notifier(new Slf4jNotifier(false)).port(httpPort));
    wireMockServer.start();
    WireMock.configureFor("localhost", httpPort);
    wireMock = new WireMock("localhost", httpPort);
    personalAccessTokenFetcher =
        new AzureDevOpsPersonalAccessTokenFetcher(
            "localhost",
            "http://localhost:3301",
            new String[] {},
            new AzureDevOpsApiClient(wireMockServer.url("/")),
            oAuthAPI);
  }

  @AfterMethod
  void stop() {
    wireMockServer.stop();
  }

  @Test
  public void fetchPersonalAccessTokenShouldReturnNullIfScmServerUrlIsNotAzureDevOps()
      throws Exception {
    PersonalAccessToken personalAccessToken =
        personalAccessTokenFetcher.fetchPersonalAccessToken(
            mock(Subject.class), "https://eclipse.org");

    assertNull(personalAccessToken);
  }

  @Test
  public void fetchPersonalAccessTokenShouldReturnToken() throws Exception {
    personalAccessTokenFetcher =
        new AzureDevOpsPersonalAccessTokenFetcher(
            "localhost", "https://dev.azure.com", new String[] {}, azureDevOpsApiClient, oAuthAPI);
    when(oAuthAPI.getOrRefreshToken(AzureDevOps.PROVIDER_NAME)).thenReturn(oAuthToken);
    when(azureDevOpsApiClient.getUserWithOAuthToken(any())).thenReturn(azureDevOpsUser);
    when(azureDevOpsUser.getEmailAddress()).thenReturn("user-email");

    PersonalAccessToken personalAccessToken =
        personalAccessTokenFetcher.fetchPersonalAccessToken(
            mock(Subject.class), "https://dev.azure.com/");

    assertNotNull(personalAccessToken);
  }

  @Test(
      expectedExceptions = ScmUnauthorizedException.class,
      expectedExceptionsMessageRegExp =
          "Username is not authorized in azure-devops OAuth provider.")
  public void shouldThrowUnauthorizedExceptionIfTokenIsNotValid() throws Exception {
    Subject subject = new SubjectImpl("Username", Collections.emptyList(), "id1", "token", false);
    OAuthToken oAuthToken = newDto(OAuthToken.class).withToken(azureOauthToken).withScope("");
    when(oAuthAPI.getOrRefreshToken(anyString())).thenReturn(oAuthToken);
    stubFor(
        get(urlEqualTo("/_apis/profile/profiles/me?api-version=7.0"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + azureOauthToken))
            .willReturn(aResponse().withStatus(HTTP_FORBIDDEN)));

    personalAccessTokenFetcher.fetchPersonalAccessToken(subject, wireMockServer.url("/"));
  }

  @Test(expectedExceptions = ScmUnauthorizedException.class)
  public void shouldThrowUnauthorizedExceptionOnRedirectResponse() throws Exception {
    Subject subject = new SubjectImpl("Username", Collections.emptyList(), "id1", "token", false);
    OAuthToken oAuthToken = newDto(OAuthToken.class).withToken(azureOauthToken).withScope("");
    when(oAuthAPI.getOrRefreshToken(anyString())).thenReturn(oAuthToken);
    stubFor(
        get(urlEqualTo("/_apis/profile/profiles/me?api-version=7.0"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + azureOauthToken))
            .willReturn(aResponse().withStatus(HTTP_MOVED_TEMP)));

    personalAccessTokenFetcher.fetchPersonalAccessToken(subject, wireMockServer.url("/"));
  }

  @Test
  public void shouldValidateSAASPersonalAccessToken() throws Exception {
    stubFor(
        get(urlEqualTo("/organization/_apis/profile/profiles/me?api-version=7.0"))
            .withHeader(
                HttpHeaders.AUTHORIZATION,
                equalTo(
                    "Basic "
                        + Base64.getEncoder().encodeToString((":" + azureDevOpsToken).getBytes())))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("azure-devops/rest/user/response.json")));

    PersonalAccessTokenParams params =
        new PersonalAccessTokenParams(
            wireMockServer.url("/"),
            "azure-devops",
            "token-name",
            "tid-23434",
            azureDevOpsToken,
            "organization");

    Optional<Pair<Boolean, String>> valid = personalAccessTokenFetcher.isValid(params);
    assertTrue(valid.isPresent());
    assertTrue(valid.get().first);
  }

  @Test
  public void shouldValidateServerPersonalAccessToken() throws Exception {
    personalAccessTokenFetcher =
        new AzureDevOpsPersonalAccessTokenFetcher(
            "localhost",
            "https://dev.azure-server.com",
            new String[] {},
            new AzureDevOpsApiClient(wireMockServer.url("/")),
            oAuthAPI);
    stubFor(
        get(urlEqualTo("/organization/_api/_common/GetUserProfile"))
            .withHeader(
                HttpHeaders.AUTHORIZATION,
                equalTo(
                    "Basic "
                        + Base64.getEncoder().encodeToString((":" + azureDevOpsToken).getBytes())))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("azure-devops-server/rest/user/response.json")));

    PersonalAccessTokenParams params =
        new PersonalAccessTokenParams(
            wireMockServer.url("/"),
            "azure-devops",
            "token-name",
            "tid-23434",
            azureDevOpsToken,
            "organization");

    Optional<Pair<Boolean, String>> valid = personalAccessTokenFetcher.isValid(params);
    assertTrue(valid.isPresent());
    assertTrue(valid.get().first);
  }

  @Test
  public void shouldValidateOauthToken() throws Exception {
    stubFor(
        get(urlEqualTo("/_apis/profile/profiles/me?api-version=7.0"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + azureDevOpsToken))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("azure-devops/rest/user/response.json")));

    PersonalAccessTokenParams params =
        new PersonalAccessTokenParams(
            wireMockServer.url("/"),
            "dev-azure",
            OAUTH_2_PREFIX + "-token-name",
            "tid-23434",
            azureDevOpsToken,
            "organization");

    Optional<Pair<Boolean, String>> valid = personalAccessTokenFetcher.isValid(params);
    assertTrue(valid.isPresent());
    assertTrue(valid.get().first);
  }
}
