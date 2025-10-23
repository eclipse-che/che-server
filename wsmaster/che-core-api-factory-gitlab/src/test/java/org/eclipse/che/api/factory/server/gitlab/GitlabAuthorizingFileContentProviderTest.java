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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import java.io.FileNotFoundException;
import java.net.URI;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.UnknownScmProviderException;
import org.eclipse.che.api.workspace.server.devfile.FileContentProvider;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.api.workspace.server.devfile.exception.DevfileException;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class GitlabAuthorizingFileContentProviderTest {
  @Mock private PersonalAccessTokenManager personalAccessTokenManager;
  @Mock private URLFetcher urlFetcher;

  private WireMockServer wireMockServer;
  private WireMock wireMock;

  @BeforeMethod
  public void start() {
    wireMockServer =
        new WireMockServer(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort());
    wireMockServer.start();
    WireMock.configureFor("localhost", wireMockServer.port());
    wireMock = new WireMock("localhost", wireMockServer.port());
  }

  @AfterMethod
  void stop() {
    wireMockServer.stop();
  }

  @Test
  public void shouldExpandRelativePaths() throws Exception {
    GitlabUrl gitlabUrl = new GitlabUrl().withHostName("gitlab.net").withSubGroups("eclipse/che");
    FileContentProvider fileContentProvider =
        new GitlabAuthorizingFileContentProvider(gitlabUrl, urlFetcher, personalAccessTokenManager);
    var personalAccessToken = new PersonalAccessToken("foo", "provider", "che", "my-token");
    when(personalAccessTokenManager.getAndStore(anyString())).thenReturn(personalAccessToken);
    fileContentProvider.fetchContent("devfile.yaml");
    verify(urlFetcher)
        .fetch(
            eq(
                "https://gitlab.net/api/v4/projects/eclipse%2Fche/repository/files/devfile.yaml/raw?ref=HEAD"),
            eq("Bearer my-token"));
  }

  @Test
  public void shouldPreserveAbsolutePaths() throws Exception {
    GitlabUrl gitlabUrl = new GitlabUrl().withHostName("gitlab.net").withSubGroups("eclipse/che");
    FileContentProvider fileContentProvider =
        new GitlabAuthorizingFileContentProvider(gitlabUrl, urlFetcher, personalAccessTokenManager);
    String url =
        "https://gitlab.net/api/v4/projects/eclipse%2Fche/repository/files/devfile.yaml/raw";
    var personalAccessToken = new PersonalAccessToken(url, "provider", "che", "my-token");
    when(personalAccessTokenManager.getAndStore(anyString())).thenReturn(personalAccessToken);

    fileContentProvider.fetchContent(url);
    verify(urlFetcher).fetch(eq(url), eq("Bearer my-token"));
  }

  @Test(expectedExceptions = FileNotFoundException.class)
  public void shouldThrowFileNotFoundException() throws Exception {
    // given
    when(urlFetcher.fetch(
            eq(
                wireMockServer.url(
                    "/api/v4/projects/eclipse%2Fche/repository/files/devfile.yaml/raw?ref=HEAD"))))
        .thenThrow(new FileNotFoundException());
    when(personalAccessTokenManager.getAndStore(anyString()))
        .thenThrow(new UnknownScmProviderException("", ""));
    URI uri = URI.create(wireMockServer.url("/"));
    GitlabUrl gitlabUrl =
        new GitlabUrl()
            .withScheme("http")
            .withHostName(format("%s:%s", uri.getHost(), uri.getPort()))
            .withSubGroups("eclipse/che");
    FileContentProvider fileContentProvider =
        new GitlabAuthorizingFileContentProvider(gitlabUrl, urlFetcher, personalAccessTokenManager);

    stubFor(get(urlEqualTo("/eclipse/che")).willReturn(aResponse().withStatus(HTTP_OK)));

    // when
    fileContentProvider.fetchContent("devfile.yaml");
  }

  @Test(
      expectedExceptions = DevfileException.class,
      expectedExceptionsMessageRegExp = "Could not reach devfile at test path")
  public void shouldThrowDevfileException() throws Exception {
    // given
    when(urlFetcher.fetch(
            eq(
                wireMockServer.url(
                    "/api/v4/projects/eclipse%2Fche/repository/files/devfile.yaml/raw?ref=HEAD"))))
        .thenThrow(new FileNotFoundException("test path"));
    when(personalAccessTokenManager.getAndStore(anyString()))
        .thenThrow(new UnknownScmProviderException("", ""));
    URI uri = URI.create(wireMockServer.url("/"));
    GitlabUrl gitlabUrl =
        new GitlabUrl()
            .withScheme("http")
            .withHostName(format("%s:%s", uri.getHost(), uri.getPort()))
            .withSubGroups("eclipse/che");
    FileContentProvider fileContentProvider =
        new GitlabAuthorizingFileContentProvider(gitlabUrl, urlFetcher, personalAccessTokenManager);

    stubFor(get(urlEqualTo("/eclipse/che")).willReturn(aResponse().withStatus(HTTP_MOVED_TEMP)));

    // when
    fileContentProvider.fetchContent("devfile.yaml");
  }
}
