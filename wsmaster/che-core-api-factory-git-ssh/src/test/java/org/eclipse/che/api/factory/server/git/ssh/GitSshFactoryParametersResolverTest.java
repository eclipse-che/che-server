/*
 * Copyright (c) 2012-2025 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.factory.server.git.ssh;

import static java.util.Collections.singletonMap;
import static org.eclipse.che.api.factory.shared.Constants.CURRENT_VERSION;
import static org.eclipse.che.api.factory.shared.Constants.URL_PARAMETER_NAME;
import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.factory.server.scm.AuthorisationRequestManager;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.api.factory.server.urlfactory.URLFactoryBuilder;
import org.eclipse.che.api.factory.shared.dto.FactoryDevfileV2Dto;
import org.eclipse.che.api.factory.shared.dto.ScmInfoDto;
import org.eclipse.che.api.workspace.server.devfile.FileContentProvider;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class GitSshFactoryParametersResolverTest {

  @Mock private DevfileFilenamesProvider devfileFilenamesProvider;
  @Mock private URLFetcher urlFetcher;
  @Mock private URLFactoryBuilder urlFactoryBuilder;
  @Mock private PersonalAccessTokenManager personalAccessTokenManager;
  @Mock private AuthorisationRequestManager authorisationRequestManager;
  @Mock private GitSshURLParser gitSshURLParser;
  @Mock private GitSshUrl gitSshUrl;
  private GitSshFactoryParametersResolver gitSshFactoryParametersResolver;

  @BeforeMethod
  protected void init() {
    gitSshFactoryParametersResolver =
        new GitSshFactoryParametersResolver(
            gitSshURLParser,
            urlFetcher,
            urlFactoryBuilder,
            personalAccessTokenManager,
            authorisationRequestManager);
  }

  @Test
  public void ShouldNotAcceptMissingParameter() {
    // given
    Map<String, String> parameters = singletonMap("foo", "this is a foo bar");
    // when
    boolean accept = gitSshFactoryParametersResolver.accept(parameters);
    // then
    assertFalse(accept);
  }

  @Test
  public void ShouldNotAcceptInvalidUrl() {
    // given
    String url = "https://provider.com/user/repo.git";
    when(gitSshURLParser.isValid(eq(url))).thenReturn(false);
    Map<String, String> parameters = singletonMap(URL_PARAMETER_NAME, url);
    // when
    boolean accept = gitSshFactoryParametersResolver.accept(parameters);
    // then
    assertFalse(accept);
  }

  @Test
  public void shouldAcceptValidUrl() {
    // given
    String url = "git@provider.com:user/repo.git";
    when(gitSshURLParser.isValid(eq(url))).thenReturn(true);
    Map<String, String> parameters = singletonMap(URL_PARAMETER_NAME, url);
    // when
    boolean accept = gitSshFactoryParametersResolver.accept(parameters);
    // then
    assertTrue(accept);
  }

  @Test
  public void shouldCreateFactoryWithDevfile() throws Exception {
    // given
    String url = "git@provider.com:user/repo.git";
    when(gitSshUrl.getProviderName()).thenReturn("git-ssh");
    when(gitSshUrl.getRepositoryLocation()).thenReturn("repository-location");
    ImmutableMap<String, String> params = ImmutableMap.of(URL_PARAMETER_NAME, url);
    when(gitSshURLParser.parse(eq(url))).thenReturn(gitSshUrl);
    when(urlFactoryBuilder.createFactoryFromDevfile(
            eq(gitSshUrl), any(FileContentProvider.class), eq(Collections.emptyMap()), eq(true)))
        .thenReturn(Optional.of(generateDevfileV2Factory()));
    // when
    FactoryDevfileV2Dto factory =
        (FactoryDevfileV2Dto) gitSshFactoryParametersResolver.createFactory(params);
    // then
    ScmInfoDto scmInfo = factory.getScmInfo();
    assertEquals(scmInfo.getScmProviderName(), "git-ssh");
    assertEquals(scmInfo.getRepositoryUrl(), "repository-location");
  }

  @Test(
      expectedExceptions = ApiException.class,
      expectedExceptionsMessageRegExp = "Failed to fetch devfile")
  public void shouldThrowException() throws Exception {
    // given
    String url = "git@provider.com:user/repo.git";
    ImmutableMap<String, String> params = ImmutableMap.of(URL_PARAMETER_NAME, url);
    when(gitSshURLParser.parse(eq(url))).thenReturn(gitSshUrl);
    when(urlFactoryBuilder.createFactoryFromDevfile(
            eq(gitSshUrl), any(FileContentProvider.class), eq(Collections.emptyMap()), eq(true)))
        .thenReturn(Optional.empty());
    // when
    FactoryDevfileV2Dto factory =
        (FactoryDevfileV2Dto) gitSshFactoryParametersResolver.createFactory(params);
  }

  private FactoryDevfileV2Dto generateDevfileV2Factory() {
    return newDto(FactoryDevfileV2Dto.class)
        .withV(CURRENT_VERSION)
        .withSource("repo")
        .withDevfile(Map.of("schemaVersion", "2.0.0"));
  }
}
