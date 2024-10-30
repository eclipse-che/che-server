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
package org.eclipse.che.api.factory.server.gitlab;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.google.common.net.HttpHeaders;
import java.lang.reflect.Field;
import java.util.Optional;
import org.eclipse.che.api.factory.server.scm.GitUserData;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.security.oauth.OAuthAPI;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class GitlabUserDataFetcherTest {

  @Mock OAuthAPI oAuthTokenFetcher;
  @Mock PersonalAccessTokenManager personalAccessTokenManager;

  GitlabUserDataFetcher gitlabUserDataFetcher;

  WireMockServer wireMockServer;
  WireMock wireMock;

  @BeforeMethod
  void start() {
    wireMockServer =
        new WireMockServer(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort());
    wireMockServer.start();
    WireMock.configureFor("localhost", wireMockServer.port());
    wireMock = new WireMock("localhost", wireMockServer.port());
    gitlabUserDataFetcher =
        new GitlabUserDataFetcher(
            wireMockServer.url("/"), "http://che.api", personalAccessTokenManager);

    stubFor(
        get(urlEqualTo("/api/v4/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer oauthtoken"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("gitlab/rest/api/v4/user/response.json")));
  }

  @AfterMethod
  void stop() {
    wireMockServer.stop();
  }

  @Test
  public void shouldFetchGitUserData() throws Exception {
    PersonalAccessToken token = mock(PersonalAccessToken.class);
    when(token.getToken()).thenReturn("oauthtoken");
    when(token.getScmProviderUrl()).thenReturn(wireMockServer.url("/"));
    when(personalAccessTokenManager.get(any(Subject.class), eq("gitlab"), eq(null)))
        .thenReturn(Optional.of(token));

    GitUserData gitUserData = gitlabUserDataFetcher.fetchGitUserData();
    assertEquals(gitUserData.getScmUsername(), "John Smith");
    assertEquals(gitUserData.getScmUserEmail(), "john@example.com");
  }

  @Test
  public void shouldSetSAASUrlAsDefault() throws Exception {
    gitlabUserDataFetcher =
        new GitlabUserDataFetcher(null, "http://che.api", personalAccessTokenManager);

    Field serverUrlField =
        gitlabUserDataFetcher.getClass().getSuperclass().getDeclaredField("serverUrl");
    serverUrlField.setAccessible(true);
    String serverUrl = (String) serverUrlField.get(gitlabUserDataFetcher);

    assertEquals(serverUrl, "https://gitlab.com");
  }
}
