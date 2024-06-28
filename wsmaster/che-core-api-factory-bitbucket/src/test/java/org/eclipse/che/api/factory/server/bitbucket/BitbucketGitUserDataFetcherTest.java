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
public class BitbucketGitUserDataFetcherTest {

  @Mock OAuthAPI oAuthAPI;
  BitbucketUserDataFetcher bitbucketUserDataFetcher;

  final int httpPort = 3301;
  WireMockServer wireMockServer;
  WireMock wireMock;

  final String bitbucketOauthToken = "bitbucket_token";

  @BeforeMethod
  void start() {
    wireMockServer =
        new WireMockServer(wireMockConfig().notifier(new Slf4jNotifier(false)).port(httpPort));
    wireMockServer.start();
    WireMock.configureFor("localhost", httpPort);
    wireMock = new WireMock("localhost", httpPort);
    bitbucketUserDataFetcher =
        new BitbucketUserDataFetcher(
            "http://che.api", oAuthAPI, new BitbucketApiClient(wireMockServer.url("/")));
    stubFor(
        get(urlEqualTo("/user"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + bitbucketOauthToken))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("bitbucket/rest/user/response.json")));
    stubFor(
        get(urlEqualTo("/user/emails"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + bitbucketOauthToken))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withBodyFile("bitbucket/rest/user/email/response.json")));
  }

  @AfterMethod
  void stop() {
    wireMockServer.stop();
  }

  @Test
  public void shouldFetchGitUserData() throws Exception {
    OAuthToken oAuthToken =
        newDto(OAuthToken.class).withToken(bitbucketOauthToken).withScope("repo");
    when(oAuthAPI.getOrRefreshToken(anyString())).thenReturn(oAuthToken);

    GitUserData gitUserData = bitbucketUserDataFetcher.fetchGitUserData();

    assertEquals(gitUserData.getScmUsername(), "Bitbucket User");
    assertEquals(gitUserData.getScmUserEmail(), "bitbucketuser@email.com");
  }
}
