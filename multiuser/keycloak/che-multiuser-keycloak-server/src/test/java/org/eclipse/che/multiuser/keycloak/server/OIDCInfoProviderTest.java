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
package org.eclipse.che.multiuser.keycloak.server;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.eclipse.che.multiuser.oidc.OIDCInfo;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class OIDCInfoProviderTest {
  private WireMockServer wireMockServer;

  private static final String CHE_REALM = "che";
  private static final String TEST_URL = "some-test-url-to-skip";

  private String serverUrl;
  private String openIdConfig;

  private static final String OPEN_ID_CONF_TEMPLATE =
      ""
          + "{"
          + "  \"token_endpoint\": \""
          + "<SERVER_URL>"
          + "/realms/"
          + CHE_REALM
          + "/protocol/openid-connect/token\","
          + "  \"end_session_endpoint\": \""
          + "<SERVER_URL>"
          + "/realms/"
          + CHE_REALM
          + "/protocol/openid-connect/logout\","
          + "  \"userinfo_endpoint\": \""
          + "<SERVER_URL>"
          + "/realms/"
          + CHE_REALM
          + "/protocol/openid-connect/userinfo\","
          + "  \"jwks_uri\": \""
          + "<SERVER_URL>"
          + "/realms/"
          + CHE_REALM
          + "/protocol/openid-connect/certs\""
          + "}";

  @BeforeClass
  void start() {
    wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
    wireMockServer.start();
    WireMock.configureFor("localhost", wireMockServer.port());

    serverUrl = "http://localhost:" + wireMockServer.port() + "/auth";
    openIdConfig = OPEN_ID_CONF_TEMPLATE.replaceAll("<SERVER_URL>", serverUrl);
  }

  @AfterClass
  void stop() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @Test(
      expectedExceptions = RuntimeException.class,
      expectedExceptionsMessageRegExp =
          "Exception while retrieving OpenId configuration from endpoint: .*")
  public void shouldFailToParseOIDCConfiguration() {
    stubFor(
        get(urlEqualTo("/auth/realms/che/.well-known/openid-configuration"))
            .willReturn(
                aResponse().withHeader("Content-Type", "text/html").withBody("broken json")));

    KeycloakOIDCInfoProvider oidcInfoProvider =
        new KeycloakOIDCInfoProvider(null, null, serverUrl, CHE_REALM);

    oidcInfoProvider.get();
  }

  @Test
  public void shouldParseOIDCConfigurationForServerUrl() {
    stubFor(
        get(urlEqualTo("/auth/realms/che/.well-known/openid-configuration"))
            .willReturn(
                aResponse().withHeader("Content-Type", "text/html").withBody(openIdConfig)));

    KeycloakOIDCInfoProvider oidcInfoProvider =
        new KeycloakOIDCInfoProvider(serverUrl, null, null, CHE_REALM);
    OIDCInfo oidcInfo = oidcInfoProvider.get();

    assertEquals(
        serverUrl + "/realms/" + CHE_REALM + "/protocol/openid-connect/token",
        oidcInfo.getTokenPublicEndpoint());
    assertEquals(
        serverUrl + "/realms/" + CHE_REALM + "/protocol/openid-connect/logout",
        oidcInfo.getEndSessionPublicEndpoint().get());
    assertNull(oidcInfo.getUserInfoInternalEndpoint());
    assertNull(oidcInfo.getJwksInternalUri());
  }

  @Test
  public void shouldParseOIDCConfigurationForInternalServerUrl() {
    String serverPublicUrl = "che-eclipse-che.apps-crc.testing";
    String OPEN_ID_CONF_TEMPLATE =
        ""
            + "{"
            + "  \"token_endpoint\": \""
            + serverPublicUrl
            + "/realms/"
            + CHE_REALM
            + "/protocol/openid-connect/token\","
            + "  \"end_session_endpoint\": \""
            + serverPublicUrl
            + "/realms/"
            + CHE_REALM
            + "/protocol/openid-connect/logout\","
            + "  \"userinfo_endpoint\": \""
            + serverPublicUrl
            + "/realms/"
            + CHE_REALM
            + "/protocol/openid-connect/userinfo\","
            + "  \"jwks_uri\": \""
            + serverPublicUrl
            + "/realms/"
            + CHE_REALM
            + "/protocol/openid-connect/certs\""
            + "}";

    stubFor(
        get(urlEqualTo("/auth/realms/che/.well-known/openid-configuration"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "text/html")
                    .withBody(OPEN_ID_CONF_TEMPLATE)));

    KeycloakOIDCInfoProvider oidcInfoProvider =
        new KeycloakOIDCInfoProvider(serverPublicUrl, serverUrl, null, CHE_REALM);
    OIDCInfo oidcInfo = oidcInfoProvider.get();

    assertEquals(
        serverPublicUrl + "/realms/" + CHE_REALM + "/protocol/openid-connect/token",
        oidcInfo.getTokenPublicEndpoint());
    assertEquals(
        serverPublicUrl + "/realms/" + CHE_REALM + "/protocol/openid-connect/logout",
        oidcInfo.getEndSessionPublicEndpoint().get());
    assertEquals(
        serverPublicUrl + "/realms/" + CHE_REALM + "/protocol/openid-connect/userinfo",
        oidcInfo.getUserInfoPublicEndpoint());
    assertEquals(
        serverPublicUrl + "/realms/" + CHE_REALM + "/protocol/openid-connect/certs",
        oidcInfo.getJwksPublicUri());

    assertEquals(
        serverUrl + "/realms/" + CHE_REALM + "/protocol/openid-connect/certs",
        oidcInfo.getJwksInternalUri());
    assertEquals(
        serverUrl + "/realms/" + CHE_REALM + "/protocol/openid-connect/userinfo",
        oidcInfo.getUserInfoInternalEndpoint());
    assertEquals(serverUrl, oidcInfo.getAuthServerURL());
  }

  @Test
  public void shouldParseOIDCConfigurationWithPublicUrlsForInternalServerUrl() {
    String serverPublicUrl = "https://keycloak-che.domain/auth";
    String serverInternalUrl = serverUrl;

    String OPEN_ID_CONF_TEMPLATE =
        ""
            + "{"
            + "  \"token_endpoint\": \""
            + serverInternalUrl
            + "/realms/"
            + CHE_REALM
            + "/protocol/openid-connect/token\","
            + "  \"end_session_endpoint\": \""
            + serverInternalUrl
            + "/realms/"
            + CHE_REALM
            + "/protocol/openid-connect/logout\","
            + "  \"userinfo_endpoint\": \""
            + serverInternalUrl
            + "/realms/"
            + CHE_REALM
            + "/protocol/openid-connect/userinfo\","
            + "  \"jwks_uri\": \""
            + serverInternalUrl
            + "/realms/"
            + CHE_REALM
            + "/protocol/openid-connect/certs\""
            + "}";

    stubFor(
        get(urlEqualTo("/auth/realms/che/.well-known/openid-configuration"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "text/html")
                    .withBody(OPEN_ID_CONF_TEMPLATE)));

    KeycloakOIDCInfoProvider oidcInfoProvider =
        new KeycloakOIDCInfoProvider(serverPublicUrl, serverInternalUrl, null, CHE_REALM);
    OIDCInfo oidcInfo = oidcInfoProvider.get();

    assertEquals(
        serverPublicUrl + "/realms/" + CHE_REALM + "/protocol/openid-connect/token",
        oidcInfo.getTokenPublicEndpoint());
    assertEquals(
        serverPublicUrl + "/realms/" + CHE_REALM + "/protocol/openid-connect/logout",
        oidcInfo.getEndSessionPublicEndpoint().get());
    assertEquals(
        serverPublicUrl + "/realms/" + CHE_REALM + "/protocol/openid-connect/userinfo",
        oidcInfo.getUserInfoPublicEndpoint());
    assertEquals(
        serverPublicUrl + "/realms/" + CHE_REALM + "/protocol/openid-connect/certs",
        oidcInfo.getJwksPublicUri());

    assertEquals(
        serverInternalUrl + "/realms/" + CHE_REALM + "/protocol/openid-connect/certs",
        oidcInfo.getJwksInternalUri());
    assertEquals(
        serverInternalUrl + "/realms/" + CHE_REALM + "/protocol/openid-connect/userinfo",
        oidcInfo.getUserInfoInternalEndpoint());

    assertEquals(serverInternalUrl, oidcInfo.getAuthServerURL());
    assertEquals(serverPublicUrl, oidcInfo.getAuthServerPublicURL());
  }

  @Test
  public void shouldParseOIDCConfigurationForOIDCProviderUrl() {
    String OIDCProviderUrl = "http://localhost:" + wireMockServer.port() + "/realms/";
    stubFor(
        get(urlEqualTo("/realms/.well-known/openid-configuration"))
            .willReturn(
                aResponse().withHeader("Content-Type", "text/html").withBody(openIdConfig)));

    KeycloakOIDCInfoProvider oidcInfoProvider =
        new KeycloakOIDCInfoProvider(TEST_URL, TEST_URL, OIDCProviderUrl, CHE_REALM);
    OIDCInfo oidcInfo = oidcInfoProvider.get();

    assertEquals(
        serverUrl + "/realms/" + CHE_REALM + "/protocol/openid-connect/token",
        oidcInfo.getTokenPublicEndpoint());
    assertEquals(
        serverUrl + "/realms/" + CHE_REALM + "/protocol/openid-connect/logout",
        oidcInfo.getEndSessionPublicEndpoint().get());
    assertEquals(
        serverUrl + "/realms/" + CHE_REALM + "/protocol/openid-connect/userinfo",
        oidcInfo.getUserInfoInternalEndpoint());
    assertEquals(
        serverUrl + "/realms/" + CHE_REALM + "/protocol/openid-connect/certs",
        oidcInfo.getJwksInternalUri());
  }

  @Test(
      expectedExceptions = RuntimeException.class,
      expectedExceptionsMessageRegExp = "Either the '.*' or '.*' or '.*' property should be set")
  public void shouldThrowErrorWhenAuthServerWasNotSet() {
    KeycloakOIDCInfoProvider oidcInfoProvider =
        new KeycloakOIDCInfoProvider(null, null, null, CHE_REALM);
    oidcInfoProvider.get();
  }

  @Test(
      expectedExceptions = RuntimeException.class,
      expectedExceptionsMessageRegExp = "The '.*' property must be set")
  public void shouldThrowErrorWhenRealmPropertyWasNotSet() {
    KeycloakOIDCInfoProvider oidcInfoProvider =
        new KeycloakOIDCInfoProvider(null, null, null, null);
    oidcInfoProvider.get();
  }
}
