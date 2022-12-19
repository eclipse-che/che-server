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
package org.eclipse.che.api.factory.server.bitbucket;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

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
public class BitbucketScmFileResolverTest {

  BitbucketURLParser bitbucketURLParser;

  @Mock private URLFetcher urlFetcher;

  @Mock private DevfileFilenamesProvider devfileFilenamesProvider;
  @Mock private PersonalAccessTokenManager personalAccessTokenManager;
  @Mock private BitbucketApiClient bitbucketApiClient;

  private BitbucketScmFileResolver bitbucketScmFileResolver;

  @BeforeMethod
  protected void init() {
    bitbucketURLParser = new BitbucketURLParser(devfileFilenamesProvider);
    assertNotNull(this.bitbucketURLParser);
    bitbucketScmFileResolver =
        new BitbucketScmFileResolver(
            bitbucketURLParser, urlFetcher, personalAccessTokenManager, bitbucketApiClient);
    assertNotNull(this.bitbucketScmFileResolver);
  }

  /** Check url which is not a Gitlab url can't be accepted by this resolver */
  @Test
  public void checkInvalidAcceptUrl() {
    // shouldn't be accepted
    assertFalse(bitbucketScmFileResolver.accept("http://foobar.com"));
  }

  /** Check <Bitbucket> url will be be accepted by this resolver */
  @Test
  public void checkValidAcceptUrl() {
    // should be accepted
    assertTrue(bitbucketScmFileResolver.accept("http://bitbucket.org/test/repo.git"));
  }

  @Test
  public void shouldReturnContentFromUrlFetcher() throws Exception {
    final String rawContent = "raw_content";
    final String filename = "devfile.yaml";
    when(bitbucketApiClient.getFileContent(
            eq("test"), eq("repo"), eq("HEAD"), eq("devfile.yaml"), eq("my-token")))
        .thenReturn(rawContent);
    var personalAccessToken = new PersonalAccessToken("foo", "che", "my-token");
    when(personalAccessTokenManager.getAndStore(anyString())).thenReturn(personalAccessToken);

    String content =
        bitbucketScmFileResolver.fileContent("http://bitbucket.org/test/repo.git", filename);

    assertEquals(content, rawContent);
  }

  @Test
  public void shouldFetchContentWithoutAuthentication() throws Exception {
    // given
    when(personalAccessTokenManager.getAndStore(anyString()))
        .thenThrow(new ScmUnauthorizedException("message", "bitbucket", "v1", "url"));

    // when
    bitbucketScmFileResolver.fileContent("https://bitbucket.org/username/repo.git", "devfile.yaml");

    // then
    verify(urlFetcher).fetch(anyString());
  }
}
