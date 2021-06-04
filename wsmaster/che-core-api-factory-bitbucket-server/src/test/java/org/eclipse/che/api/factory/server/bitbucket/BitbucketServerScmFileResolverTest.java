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
package org.eclipse.che.api.factory.server.bitbucket;

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
public class BitbucketServerScmFileResolverTest {

  public static final String SCM_URL = "https://foo.bar";
  BitbucketURLParser bitbucketURLParser;

  @Mock private URLFetcher urlFetcher;

  @Mock private DevfileFilenamesProvider devfileFilenamesProvider;

  @Mock private GitCredentialManager gitCredentialManager;
  @Mock private PersonalAccessTokenManager personalAccessTokenManager;

  private BitbucketServerScmFileResolver serverScmFileResolver;

  @BeforeMethod
  protected void init() {
    bitbucketURLParser = new BitbucketURLParser(SCM_URL, devfileFilenamesProvider);
    assertNotNull(this.bitbucketURLParser);
    serverScmFileResolver =
        new BitbucketServerScmFileResolver(
            bitbucketURLParser, urlFetcher, gitCredentialManager, personalAccessTokenManager);
    assertNotNull(this.serverScmFileResolver);
  }

  /** Check url which is not a bitbucket url can't be accepted by this resolver */
  @Test
  public void checkInvalidAcceptUrl() {
    // shouldn't be accepted
    assertFalse(serverScmFileResolver.accept("http://github.com"));
  }

  /** Check bitbucket url will be be accepted by this resolver */
  @Test
  public void checkValidAcceptUrl() {
    // should be accepted
    assertTrue(serverScmFileResolver.accept("https://foo.bar/scm/test/repo.git"));
  }

  @Test
  public void shouldReturnContentFromUrlFetcher() throws Exception {
    final String rawContent = "raw_content";
    final String filename = "devfile.yaml";
    when(personalAccessTokenManager.get(any(Subject.class), anyString()))
        .thenReturn(Optional.of(new PersonalAccessToken(SCM_URL, "root", "token123")));

    when(urlFetcher.fetch(anyString(), eq("Bearer token123"))).thenReturn(rawContent);

    String content =
        serverScmFileResolver.fileContent("https://foo.bar/scm/test/repo.git", filename);

    assertEquals(content, rawContent);
  }
}
