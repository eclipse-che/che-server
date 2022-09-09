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
package org.eclipse.che.api.factory.server.scm;

import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.eclipse.che.commons.subject.Subject;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(value = {MockitoTestNGListener.class})
public class AuthorizingFactoryParameterResolverTest {
  @Mock private RemoteFactoryUrl remoteFactoryUrl;
  @Mock private URLFetcher urlFetcher;
  @Mock private PersonalAccessTokenManager personalAccessTokenManager;
  @Mock private GitCredentialManager gitCredentialManager;
  @Mock private PersonalAccessToken personalAccessToken;

  private AuthorizingFileContentProvider<?> provider;

  @BeforeMethod
  public void setUp() throws Exception {
    provider =
        new AuthorizingFileContentProvider<>(
            remoteFactoryUrl, urlFetcher, personalAccessTokenManager, gitCredentialManager);
    when(remoteFactoryUrl.rawFileLocation(anyString())).thenAnswer(returnsFirstArg());
  }

  @Test
  public void shouldFetchContentWithAuthentication() throws Exception {
    // given
    when(remoteFactoryUrl.getHostName()).thenReturn("hostName");
    when(urlFetcher.fetch(anyString(), anyString())).thenReturn("content");
    when(personalAccessTokenManager.fetchAndSave(any(Subject.class), anyString()))
        .thenReturn(personalAccessToken);

    // when
    provider.fetchContent("url");

    // then
    verify(personalAccessTokenManager).fetchAndSave(any(Subject.class), anyString());
  }

  @Test
  public void shouldFetchContentWithoutAuthentication() throws Exception {
    // given
    when(remoteFactoryUrl.getHostName()).thenReturn("hostName");
    when(urlFetcher.fetch(anyString())).thenReturn("content");

    // when
    provider.fetchContentWithoutAuthentication("url");

    // then
    verify(personalAccessTokenManager, never()).fetchAndSave(any(Subject.class), anyString());
  }

  @Test
  public void shouldOmitDotInTheResourceName() throws Exception {
    assertEquals(provider.formatUrl("./pom.xml"), "pom.xml");
  }

  @Test
  public void shouldKeepResourceNameUnchanged() throws Exception {
    assertEquals(provider.formatUrl(".gitconfig"), ".gitconfig");
  }
}
