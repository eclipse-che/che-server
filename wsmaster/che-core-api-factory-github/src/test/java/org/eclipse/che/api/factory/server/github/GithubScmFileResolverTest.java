/*
 * Copyright (c) 2012-2023 Red Hat, Inc.
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class GithubScmFileResolverTest {

  @Mock private DevfileFilenamesProvider devfileFilenamesProvider;

  GithubURLParser githubURLParser;

  private URLFetcher urlFetcher;

  private PersonalAccessTokenManager personalAccessTokenManager;

  private GithubScmFileResolver githubScmFileResolver;

  private GithubApiClient githubApiClient;

  @BeforeMethod
  void start() {
    this.githubApiClient = mock(GithubApiClient.class);
    this.urlFetcher = mock(URLFetcher.class);

    this.personalAccessTokenManager = mock(PersonalAccessTokenManager.class);

    githubURLParser =
        new GithubURLParser(
            personalAccessTokenManager, devfileFilenamesProvider, githubApiClient, null, false);

    githubScmFileResolver =
        new GithubScmFileResolver(githubURLParser, urlFetcher, personalAccessTokenManager);
  }

  /** Check url which is not a Gitlab url can't be accepted by this resolver */
  @Test
  public void checkInvalidAcceptUrl() {
    // shouldn't be accepted
    assertFalse(githubScmFileResolver.accept("http://foobar.com"));
  }

  /** Check <GitHub> url will be be accepted by this resolver */
  @Test
  public void checkValidAcceptUrl() {
    // should be accepted
    assertTrue(githubScmFileResolver.accept("https://github.com/test/repo.git"));
  }

  @Test
  public void shouldReturnContentFromUrlFetcher() throws Exception {
    final String rawContent = "raw_content";
    final String filename = "devfile.yaml";

    when(urlFetcher.fetch(
            eq(
                "https://raw.githubusercontent.com/organization/samples/d74923ebf968454cf13251f17df69dcd87d3b932/devfile.yaml"),
            anyString()))
        .thenReturn(rawContent);

    lenient()
        .when(personalAccessTokenManager.getAndStore(anyString()))
        .thenReturn(new PersonalAccessToken("foo", "che", "my-token"));

    when(githubApiClient.isConnected(eq("https://github.com"))).thenReturn(true);
    when(githubApiClient.getLatestCommit(anyString(), anyString(), anyString(), any()))
        .thenReturn(
            new GithubCommit()
                .withSha("d74923ebf968454cf13251f17df69dcd87d3b932")
                .withUrl("http://commit.url"));

    String content =
        githubScmFileResolver.fileContent("https://github.com/organization/samples.git", filename);

    assertEquals(content, rawContent);
  }

  @Test
  public void shouldReturnContentWithoutAuthentication() throws Exception {
    // given
    lenient()
        .when(personalAccessTokenManager.getAndStore(anyString()))
        .thenThrow(new ScmUnauthorizedException("message", "github", "v1", "url"));

    // when
    githubScmFileResolver.fileContent("https://github.com/username/repo.git", "devfile.yaml");

    // then
    verify(urlFetcher).fetch(anyString());
  }
}
