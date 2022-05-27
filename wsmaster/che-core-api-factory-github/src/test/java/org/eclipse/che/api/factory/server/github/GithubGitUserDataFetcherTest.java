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
public class GithubGitUserDataFetcherTest {

  @Mock OAuthAPI oAuthAPI;
  GithubUserDataFetcher githubGUDFetcher;

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
    githubGUDFetcher =
        new GithubUserDataFetcher(
            "http://che.api", oAuthAPI, new GithubApiClient(wireMockServer.url("/")));
    stubFor(
        get(urlEqualTo("/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token " + githubOauthToken))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader(GithubApiClient.GITHUB_OAUTH_SCOPES_HEADER, "repo")
                    .withBodyFile("github/rest/user/response.json")));
  }

  @AfterMethod
  void stop() {
    wireMockServer.stop();
  }

  @Test
  public void shouldFetchGitUserData() throws Exception {
    OAuthToken oAuthToken = newDto(OAuthToken.class).withToken(githubOauthToken).withScope("repo");
    when(oAuthAPI.getToken(anyString())).thenReturn(oAuthToken);

    GitUserData gitUserData = githubGUDFetcher.fetchGitUserData();

    assertEquals(gitUserData.getScmUsername(), "Github User");
    assertEquals(gitUserData.getScmUserEmail(), "github-user@acme.com");
  }
}
