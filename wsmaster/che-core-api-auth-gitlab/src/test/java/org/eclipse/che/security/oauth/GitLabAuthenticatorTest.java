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
package org.eclipse.che.security.oauth;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import com.google.common.net.HttpHeaders;
import java.lang.reflect.Field;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class GitLabAuthenticatorTest {
  WireMockServer wireMockServer;
  WireMock wireMock;

  @BeforeClass
  public void setup() {
    wireMockServer =
        new WireMockServer(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort());
    wireMockServer.start();
    WireMock.configureFor("localhost", wireMockServer.port());
    wireMock = new WireMock("localhost", wireMockServer.port());
  }

  @Test
  public void shouldGetToken() throws Exception {
    // given
    GitLabOAuthAuthenticator gitLabOAuthAuthenticator =
        new GitLabOAuthAuthenticator(
            "id", "secret", wireMockServer.url("/"), "https://che.api.com", "gitlab");
    Field flowField = OAuthAuthenticator.class.getDeclaredField("flow");
    Field credentialDataStoreField =
        ((Class) flowField.getGenericType()).getDeclaredField("credentialDataStore");
    credentialDataStoreField.setAccessible(true);
    credentialDataStoreField.set(
        flowField.get(gitLabOAuthAuthenticator),
        new MemoryDataStoreFactory()
            .getDataStore("test")
            .set("userId", new StoredCredential().setAccessToken("token")));
    stubFor(
        get(urlEqualTo("/api/v4/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer token"))
            .willReturn(aResponse().withBody("{\"id\": \"testId\"}")));
    // when
    OAuthToken token = gitLabOAuthAuthenticator.getOrRefreshToken("userId");
    // then
    assertEquals(token.getToken(), "token");
  }

  @Test
  public void shouldGetEmptyToken() throws Exception {
    // given
    GitLabOAuthAuthenticator gitLabOAuthAuthenticator =
        new GitLabOAuthAuthenticator(
            "id", "secret", wireMockServer.url("/"), "https://che.api.com", "gitlab");
    Field flowField = OAuthAuthenticator.class.getDeclaredField("flow");
    Field credentialDataStoreField =
        ((Class) flowField.getGenericType()).getDeclaredField("credentialDataStore");
    credentialDataStoreField.setAccessible(true);
    credentialDataStoreField.set(
        flowField.get(gitLabOAuthAuthenticator),
        new MemoryDataStoreFactory()
            .getDataStore("test")
            .set("userId", new StoredCredential().setAccessToken("token")));
    stubFor(
        get(urlEqualTo("/api/v4/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer token"))
            .willReturn(aResponse().withBody("{}")));
    // when
    OAuthToken token = gitLabOAuthAuthenticator.getOrRefreshToken("userId");
    // then
    assertNull(token);
  }
}
