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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.testng.Assert.assertEquals;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.google.gson.JsonSyntaxException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class KeycloakPasswordGrantTokenRequesterTest {
  private WireMockServer wireMockServer;
  private String path;
  private String OIDCProviderUrl;

  @BeforeClass
  void start() {
    wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
    wireMockServer.start();
    WireMock.configureFor("localhost", wireMockServer.port());
    path = "/auth/realms/master/protocol/openid-connect/token";
    OIDCProviderUrl = "http://localhost:" + wireMockServer.port() + path;
  }

  @AfterClass
  void stop() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @Test
  public void shouldRetrieveAccessToken() throws Exception {
    String expectedToken =
        "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJ4NkRsYjhFS3VuVDQ3Ui1YRG1JdWJkcEo0";
    stubFor(
        post(urlEqualTo(path))
            .withRequestBody(
                matching(
                    "grant_type=password&username=admin&password=password&client_id=admin-cli"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"access_token\": \"" + expectedToken + "\"}")));

    KeycloakPasswordGrantTokenRequester r = new KeycloakPasswordGrantTokenRequester();
    String actualToken = r.requestToken("admin", "password", OIDCProviderUrl);
    assertEquals(actualToken, expectedToken);
  }

  @Test(expectedExceptions = JsonSyntaxException.class)
  public void shouldThrowJsonSyntaxException() throws Exception {
    stubFor(
        post(urlEqualTo(path))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("MALFORMED JSON")));

    KeycloakPasswordGrantTokenRequester r = new KeycloakPasswordGrantTokenRequester();
    r.requestToken("admin", "password", OIDCProviderUrl);
  }
}
