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
package org.eclipse.che.security.oauth;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import java.io.IOException;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/** Tests for {@link OAuthAuthenticator}. */
public class OAuthAuthenticatorTest {

  private static final String USER_ID = "user1";
  private static final String TOKEN_PATH = "/oauth/token";

  private WireMockServer wireMockServer;
  private TestOAuthAuthenticator authenticator;

  @BeforeClass
  void start() {
    wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
    wireMockServer.start();
    WireMock.configureFor("localhost", wireMockServer.port());
  }

  @AfterClass
  void stop() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @BeforeMethod
  void setUp() throws IOException {
    wireMockServer.resetAll();
    authenticator = new TestOAuthAuthenticator(wireMockServer.baseUrl() + TOKEN_PATH);
  }

  @Test
  public void shouldReturnNullWhenNoCredentialStored() throws Exception {
    assertNull(authenticator.getOrRefreshToken(USER_ID));
  }

  @Test
  public void shouldReturnTokenWithExpirationWhenCredentialHasExpiry() throws Exception {
    // given
    storeCredential("access-token", "refresh-token", 3600L);

    // when
    OAuthToken token = authenticator.getOrRefreshToken(USER_ID);

    // then
    assertEquals(token.getToken(), "access-token");
    assertEquals(token.getRefreshToken(), "refresh-token");
    assertTrue(
        token.getExpiresIn() > 0 && token.getExpiresIn() <= 3600,
        "Unexpected expiration time: " + token.getExpiresIn());
  }

  /** Providers that issue non-expiring tokens omit {@code expires_in} from the token response. */
  @Test
  public void shouldReturnTokenWithoutExpirationWhenCredentialHasNoExpiry() throws Exception {
    // given
    storeCredential("access-token", "refresh-token", null);

    // when
    OAuthToken token = authenticator.getOrRefreshToken(USER_ID);

    // then
    assertEquals(token.getToken(), "access-token");
    assertEquals(token.getRefreshToken(), "refresh-token");
    assertEquals(token.getExpiresIn(), 0L);
  }

  @Test
  public void shouldReturnNullOnRefreshWhenNoCredentialStored() throws Exception {
    assertNull(authenticator.refreshToken(USER_ID));
  }

  @Test
  public void shouldReturnRefreshedTokenWithExpiration() throws Exception {
    // given
    storeCredential("access-token", "refresh-token", 3600L);
    stubTokenEndpoint(
        "{\"access_token\":\"new-access-token\",\"refresh_token\":\"new-refresh-token\","
            + "\"expires_in\":7200,\"token_type\":\"Bearer\"}");

    // when
    OAuthToken token = authenticator.refreshToken(USER_ID);

    // then
    assertEquals(token.getToken(), "new-access-token");
    assertEquals(token.getRefreshToken(), "new-refresh-token");
    assertTrue(
        token.getExpiresIn() > 0 && token.getExpiresIn() <= 7200,
        "Unexpected expiration time: " + token.getExpiresIn());
  }

  @Test
  public void shouldReturnRefreshedTokenWhenResponseHasNoExpiresIn() throws Exception {
    // given
    storeCredential("access-token", "refresh-token", 3600L);
    stubTokenEndpoint(
        "{\"access_token\":\"new-access-token\",\"refresh_token\":\"new-refresh-token\","
            + "\"token_type\":\"Bearer\"}");

    // when
    OAuthToken token = authenticator.refreshToken(USER_ID);

    // then
    assertEquals(token.getToken(), "new-access-token");
    assertEquals(token.getRefreshToken(), "new-refresh-token");
    assertEquals(token.getExpiresIn(), 0L);
  }

  @Test
  public void shouldInvalidateCredentialWhenRefreshFails() throws Exception {
    // given
    storeCredential("access-token", "refresh-token", 3600L);
    stubFor(post(urlPathEqualTo(TOKEN_PATH)).willReturn(aResponse().withStatus(400)));

    // when
    OAuthToken token = authenticator.refreshToken(USER_ID);

    // then
    assertNull(token);
    assertNull(authenticator.flow.loadCredential(USER_ID));
  }

  private void storeCredential(String accessToken, String refreshToken, Long expiresInSeconds)
      throws IOException {
    authenticator.flow.createAndStoreCredential(
        new TokenResponse()
            .setAccessToken(accessToken)
            .setRefreshToken(refreshToken)
            .setExpiresInSeconds(expiresInSeconds),
        USER_ID);
  }

  private void stubTokenEndpoint(String body) {
    stubFor(
        post(urlPathEqualTo(TOKEN_PATH))
            .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(body)));
  }

  private static class TestOAuthAuthenticator extends OAuthAuthenticator {

    TestOAuthAuthenticator(String tokenUri) throws IOException {
      configure(
          "clientId",
          "clientSecret",
          new String[] {"http://localhost/callback"},
          "http://localhost/auth",
          tokenUri,
          new MemoryDataStoreFactory());
    }

    @Override
    public String getOAuthProvider() {
      return "test";
    }

    @Override
    public String getEndpointUrl() {
      return "http://localhost";
    }
  }
}
