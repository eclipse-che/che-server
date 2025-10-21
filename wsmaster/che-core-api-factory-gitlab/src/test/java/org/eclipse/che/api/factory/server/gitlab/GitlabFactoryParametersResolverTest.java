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

import static java.util.Collections.singletonMap;
import static org.eclipse.che.api.factory.shared.Constants.CURRENT_VERSION;
import static org.eclipse.che.api.factory.shared.Constants.URL_PARAMETER_NAME;
import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.eclipse.che.security.oauth1.OAuthAuthenticationService.ERROR_QUERY_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.model.factory.ScmInfo;
import org.eclipse.che.api.factory.server.scm.AuthorisationRequestManager;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl;
import org.eclipse.che.api.factory.server.urlfactory.URLFactoryBuilder;
import org.eclipse.che.api.factory.shared.dto.FactoryDevfileV2Dto;
import org.eclipse.che.api.factory.shared.dto.ScmInfoDto;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Validate operations performed by the Github Factory service
 *
 * @author Florent Benoit
 */
@Listeners(MockitoTestNGListener.class)
public class GitlabFactoryParametersResolverTest {

  @Mock private URLFactoryBuilder urlFactoryBuilder;

  @Mock private URLFetcher urlFetcher;

  @Mock private DevfileFilenamesProvider devfileFilenamesProvider;

  GitlabUrlParser gitlabUrlParser;
  @Mock private PersonalAccessTokenManager personalAccessTokenManager;

  @Mock private AuthorisationRequestManager authorisationRequestManager;

  private GitlabFactoryParametersResolver gitlabFactoryParametersResolver;

  @BeforeMethod
  protected void init() {
    gitlabUrlParser =
        new GitlabUrlParser(
            "http://gitlab.2mcl.com",
            devfileFilenamesProvider,
            mock(PersonalAccessTokenManager.class));
    assertNotNull(this.gitlabUrlParser);
    gitlabFactoryParametersResolver =
        new GitlabFactoryParametersResolver(
            urlFactoryBuilder,
            urlFetcher,
            gitlabUrlParser,
            personalAccessTokenManager,
            authorisationRequestManager);
    assertNotNull(this.gitlabFactoryParametersResolver);
  }

  /** Check url which is not a Gitlab url can't be accepted by this resolver */
  @Test
  public void checkInvalidAcceptUrl() {
    Map<String, String> parameters =
        singletonMap(URL_PARAMETER_NAME, "http://github.com/user/repo");
    // shouldn't be accepted
    assertFalse(gitlabFactoryParametersResolver.accept(parameters));
  }

  /** Check Gitlab url will be be accepted by this resolver */
  @Test
  public void checkValidAcceptUrl() {
    Map<String, String> parameters =
        singletonMap(URL_PARAMETER_NAME, "http://gitlab.2mcl.com/test/proj/repo.git");
    // should be accepted
    assertTrue(gitlabFactoryParametersResolver.accept(parameters));
  }

  @Test
  public void shouldGenerateDevfileForFactoryWithNoDevfileOrJson() throws Exception {

    String gitlabUrl = "http://gitlab.2mcl.com/test/proj/repo.git";

    when(urlFactoryBuilder.createFactoryFromDevfile(
            any(RemoteFactoryUrl.class), any(), anyMap(), anyBoolean()))
        .thenReturn(Optional.empty());
    Map<String, String> params = ImmutableMap.of(URL_PARAMETER_NAME, gitlabUrl);
    // when
    FactoryDevfileV2Dto factory =
        (FactoryDevfileV2Dto) gitlabFactoryParametersResolver.createFactory(params);
    // then
    ScmInfoDto scmInfo = factory.getScmInfo();
    assertEquals(scmInfo.getRepositoryUrl(), gitlabUrl);
    assertEquals(scmInfo.getBranch(), null);
  }

  @Test
  public void shouldSetDefaultProjectIntoDevfileIfNotSpecified() throws Exception {

    String gitlabUrl = "http://gitlab.2mcl.com/test/proj/repo/-/tree/foobar";

    FactoryDevfileV2Dto computedFactory = generateDevfileV2Factory();

    when(urlFactoryBuilder.createFactoryFromDevfile(
            any(RemoteFactoryUrl.class), any(), anyMap(), anyBoolean()))
        .thenReturn(Optional.of(computedFactory));

    Map<String, String> params = ImmutableMap.of(URL_PARAMETER_NAME, gitlabUrl);
    // when
    FactoryDevfileV2Dto factory =
        (FactoryDevfileV2Dto) gitlabFactoryParametersResolver.createFactory(params);
    // then
    assertNotNull(factory.getDevfile());
    ScmInfoDto source = factory.getScmInfo();
    assertEquals(source.getRepositoryUrl(), "http://gitlab.2mcl.com/test/proj/repo.git");
    assertEquals(source.getBranch(), "foobar");
  }

  @Test
  public void shouldCreateFactoryWithoutAuthentication() throws ApiException {
    // given
    String gitlabUrl = "https://gitlab.com/user/repo.git";
    Map<String, String> params =
        ImmutableMap.of(URL_PARAMETER_NAME, gitlabUrl, ERROR_QUERY_NAME, "access_denied");
    when(urlFactoryBuilder.createFactoryFromDevfile(
            any(RemoteFactoryUrl.class), any(), anyMap(), anyBoolean()))
        .thenReturn(Optional.of(generateDevfileV2Factory()));

    // when
    gitlabFactoryParametersResolver.createFactory(params);

    // then
    verify(urlFactoryBuilder)
        .createFactoryFromDevfile(
            any(GitlabUrl.class),
            any(GitlabAuthorizingFileContentProvider.class),
            anyMap(),
            eq(true));
  }

  @Test
  public void shouldSetScmInfoIntoDevfileV2() throws Exception {

    String gitlabUrl = "http://gitlab.2mcl.com/eclipse/che/-/tree/foobar";

    FactoryDevfileV2Dto computedFactory = generateDevfileV2Factory();

    when(urlFactoryBuilder.createFactoryFromDevfile(
            any(RemoteFactoryUrl.class), any(), anyMap(), anyBoolean()))
        .thenReturn(Optional.of(computedFactory));

    Map<String, String> params = ImmutableMap.of(URL_PARAMETER_NAME, gitlabUrl);
    // when
    FactoryDevfileV2Dto factory =
        (FactoryDevfileV2Dto) gitlabFactoryParametersResolver.createFactory(params);
    // then
    ScmInfo scmInfo = factory.getScmInfo();
    assertNotNull(scmInfo);
    assertEquals(scmInfo.getScmProviderName(), "gitlab");
    assertEquals(scmInfo.getRepositoryUrl(), "http://gitlab.2mcl.com/eclipse/che.git");
    assertEquals(scmInfo.getBranch(), "foobar");
  }

  private FactoryDevfileV2Dto generateDevfileV2Factory() {
    return newDto(FactoryDevfileV2Dto.class)
        .withV(CURRENT_VERSION)
        .withSource("repo")
        .withDevfile(Map.of("schemaVersion", "2.0.0"));
  }
}
