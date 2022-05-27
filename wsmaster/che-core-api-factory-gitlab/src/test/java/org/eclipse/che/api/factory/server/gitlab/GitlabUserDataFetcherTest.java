/*
 * Copyright (c) 2012-2022 Red Hat, Inc.
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
import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.google.common.net.HttpHeaders;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.factory.server.scm.GitUserData;
import org.eclipse.che.security.oauth.OAuthAPI;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class GitlabUserDataFetcherTest {

  @Mock OAuthAPI oAuthAPI;

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
            wireMockServer.url("/"), wireMockServer.url("/"), "http://che.api", oAuthAPI);

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
    OAuthToken oAuthToken =
        newDto(OAuthToken.class).withToken("oauthtoken").withScope("api write_repository openid");
    when(oAuthAPI.getToken(anyString())).thenReturn(oAuthToken);

    GitUserData gitUserData = gitlabUserDataFetcher.fetchGitUserData();
    assertEquals(gitUserData.getScmUsername(), "John Smith");
    assertEquals(gitUserData.getScmUserEmail(), "john@example.com");
  }
}
