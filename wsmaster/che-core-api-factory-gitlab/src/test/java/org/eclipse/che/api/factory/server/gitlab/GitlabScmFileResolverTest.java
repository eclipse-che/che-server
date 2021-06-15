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
package org.eclipse.che.api.factory.server.gitlab;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

import java.util.Optional;
import org.eclipse.che.api.factory.server.scm.GitCredentialManager;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.commons.subject.Subject;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class GitlabScmFileResolverTest {

  public static final String SCM_URL = "http://gitlab.2mcl.com";
  GitlabUrlParser gitlabUrlParser;

  @Mock private URLFetcher urlFetcher;

  @Mock private DevfileFilenamesProvider devfileFilenamesProvider;

  @Mock private GitCredentialManager gitCredentialManager;
  @Mock private PersonalAccessTokenManager personalAccessTokenManager;

  private GitlabScmFileResolver gitlabScmFileResolver;

  @BeforeMethod
  protected void init() {
    gitlabUrlParser = new GitlabUrlParser(SCM_URL, devfileFilenamesProvider);
    assertNotNull(this.gitlabUrlParser);
    gitlabScmFileResolver =
        new GitlabScmFileResolver(
            gitlabUrlParser, urlFetcher, gitCredentialManager, personalAccessTokenManager);
    assertNotNull(this.gitlabScmFileResolver);
  }

  /** Check url which is not a Gitlab url can't be accepted by this resolver */
  @Test
  public void checkInvalidAcceptUrl() {
    // shouldn't be accepted
    assertFalse(gitlabScmFileResolver.accept("http://github.com"));
  }

  /** Check Gitlab url will be be accepted by this resolver */
  @Test
  public void checkValidAcceptUrl() {
    // should be accepted
    assertTrue(gitlabScmFileResolver.accept("http://gitlab.2mcl.com/test/proj/repo.git"));
  }

  @Test
  public void shouldReturnContentFromUrlFetcher() throws Exception {
    final String rawContent = "raw_content";
    final String filename = "devfile.yaml";
    when(personalAccessTokenManager.get(any(Subject.class), anyString()))
        .thenReturn(Optional.of(new PersonalAccessToken(SCM_URL, "root", "token123")));

    when(urlFetcher.fetch(anyString(), eq("Bearer token123"))).thenReturn(rawContent);

    String content =
        gitlabScmFileResolver.fileContent("http://gitlab.2mcl.com/test/proj/repo.git", filename);

    assertEquals(content, rawContent);
  }
}
