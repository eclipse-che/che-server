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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

import org.eclipse.che.api.factory.server.scm.GitCredentialManager;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class GithubScmFileResolverTest {

  GithubURLParser githubURLParser;

  @Mock private URLFetcher urlFetcher;

  @Mock private DevfileFilenamesProvider devfileFilenamesProvider;

  @Mock private GitCredentialManager gitCredentialManager;
  @Mock private PersonalAccessTokenManager personalAccessTokenManager;

  private GithubScmFileResolver githubScmFileResolver;

  @BeforeMethod
  protected void init() {
    githubURLParser = new GithubURLParser(urlFetcher, devfileFilenamesProvider);
    assertNotNull(this.githubURLParser);
    githubScmFileResolver =
        new GithubScmFileResolver(
            githubURLParser, urlFetcher, gitCredentialManager, personalAccessTokenManager);
    assertNotNull(this.githubScmFileResolver);
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
    assertTrue(githubScmFileResolver.accept("http://github.com/test/repo.git"));
  }

  @Test
  public void shouldReturnContentFromUrlFetcher() throws Exception {
    final String rawContent = "raw_content";
    final String filename = "devfile.yaml";
    when(urlFetcher.fetch(anyString(), anyString())).thenReturn(rawContent);
    var personalAccessToken = new PersonalAccessToken("foo", "che", "my-token");
    when(personalAccessTokenManager.fetchAndSave(any(), anyString()))
        .thenReturn(personalAccessToken);

    String content = githubScmFileResolver.fileContent("http://github.com/test/repo.git", filename);

    assertEquals(content, rawContent);
  }
}
