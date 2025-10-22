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
import org.eclipse.che.security.oauth.OAuthAPI;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class BitbucketServerAuthorizingFactoryParametersResolverTest {

  @Mock private OAuthAPI oAuthAPI;
  @Mock private URLFactoryBuilder urlFactoryBuilder;

  @Mock private URLFetcher urlFetcher;

  @Mock private DevfileFilenamesProvider devfileFilenamesProvider;

  @Mock private AuthorisationRequestManager authorisationRequestManager;

  BitbucketServerURLParser bitbucketURLParser;
  @Mock private PersonalAccessTokenManager personalAccessTokenManager;

  private BitbucketServerAuthorizingFactoryParametersResolver
      bitbucketServerFactoryParametersResolver;

  @BeforeMethod
  protected void init() {
    bitbucketURLParser =
        new BitbucketServerURLParser(
            "http://bitbucket.2mcl.com",
            devfileFilenamesProvider,
            oAuthAPI,
            mock(PersonalAccessTokenManager.class));
    assertNotNull(this.bitbucketURLParser);
    bitbucketServerFactoryParametersResolver =
        new BitbucketServerAuthorizingFactoryParametersResolver(
            urlFactoryBuilder,
            urlFetcher,
            bitbucketURLParser,
            personalAccessTokenManager,
            authorisationRequestManager);
    assertNotNull(this.bitbucketServerFactoryParametersResolver);
  }

  /** Check url which is not a bitbucket url can't be accepted by this resolver */
  @Test
  public void checkInvalidAcceptUrl() {
    Map<String, String> parameters = singletonMap(URL_PARAMETER_NAME, "http://github.com");
    // shouldn't be accepted
    assertFalse(bitbucketServerFactoryParametersResolver.accept(parameters));
  }

  /** Check bitbucket url will be be accepted by this resolver */
  @Test
  public void checkValidAcceptUrl() {
    Map<String, String> parameters =
        singletonMap(URL_PARAMETER_NAME, "http://bitbucket.2mcl.com/scm/test/repo.git");
    // should be accepted
    assertTrue(bitbucketServerFactoryParametersResolver.accept(parameters));
  }

  @Test
  public void shouldGenerateDevfileForFactoryWithNoDevfileOrJson() throws Exception {

    String bitbucketUrl = "http://bitbucket.2mcl.com/scm/test/repo.git";

    when(urlFactoryBuilder.createFactoryFromDevfile(
            any(RemoteFactoryUrl.class), any(), anyMap(), anyBoolean()))
        .thenReturn(Optional.empty());
    Map<String, String> params = ImmutableMap.of(URL_PARAMETER_NAME, bitbucketUrl);
    // when
    FactoryDevfileV2Dto factory =
        (FactoryDevfileV2Dto) bitbucketServerFactoryParametersResolver.createFactory(params);
    // then
    ScmInfoDto scmInfo = factory.getScmInfo();
    assertEquals(scmInfo.getRepositoryUrl(), bitbucketUrl);
    assertEquals(scmInfo.getBranch(), null);
  }

  @Test
  public void shouldSetDefaultProjectIntoDevfileIfNotSpecified() throws Exception {

    String bitbucketUrl =
        "http://bitbucket.2mcl.com/users/test/repos/repo/browse?at=refs%2Fheads%2Ffoobar";

    FactoryDevfileV2Dto factoryDevfileV2Dto = generateDevfileV2Factory();

    when(urlFactoryBuilder.createFactoryFromDevfile(
            any(RemoteFactoryUrl.class), any(), anyMap(), anyBoolean()))
        .thenReturn(Optional.of(factoryDevfileV2Dto));

    Map<String, String> params = ImmutableMap.of(URL_PARAMETER_NAME, bitbucketUrl);
    // when
    FactoryDevfileV2Dto factory =
        (FactoryDevfileV2Dto) bitbucketServerFactoryParametersResolver.createFactory(params);
    // then
    assertNotNull(factory.getDevfile());
    ScmInfoDto source = factory.getScmInfo();
    assertEquals(source.getRepositoryUrl(), "http://bitbucket.2mcl.com/scm/~test/repo.git");
    assertEquals(source.getBranch(), "foobar");
  }

  @Test
  public void shouldSetScmInfoIntoDevfileV2() throws Exception {

    String bitbucketUrl =
        "http://bitbucket.2mcl.com/users/test/repos/repo/browse?at=refs%2Fheads%2Ffoobar";

    FactoryDevfileV2Dto computedFactory = generateDevfileV2Factory();

    when(urlFactoryBuilder.createFactoryFromDevfile(
            any(RemoteFactoryUrl.class), any(), anyMap(), anyBoolean()))
        .thenReturn(Optional.of(computedFactory));

    Map<String, String> params = ImmutableMap.of(URL_PARAMETER_NAME, bitbucketUrl);
    // when
    FactoryDevfileV2Dto factory =
        (FactoryDevfileV2Dto) bitbucketServerFactoryParametersResolver.createFactory(params);
    // then
    ScmInfo scmInfo = factory.getScmInfo();
    assertNotNull(scmInfo);
    assertEquals(scmInfo.getScmProviderName(), "bitbucket");
    assertEquals(scmInfo.getRepositoryUrl(), "http://bitbucket.2mcl.com/scm/~test/repo.git");
    assertEquals(scmInfo.getBranch(), "foobar");
  }

  private FactoryDevfileV2Dto generateDevfileV2Factory() {
    return newDto(FactoryDevfileV2Dto.class)
        .withV(CURRENT_VERSION)
        .withSource("repo")
        .withDevfile(Map.of("schemaVersion", "2.0.0"));
  }

  @Test
  public void shouldCreateFactoryWithoutAuthentication() throws ApiException {
    // given
    String bitbucketServerUrl = "http://bitbucket.2mcl.com/scm/~user/repo.git";
    Map<String, String> params =
        ImmutableMap.of(URL_PARAMETER_NAME, bitbucketServerUrl, ERROR_QUERY_NAME, "access_denied");
    when(urlFactoryBuilder.createFactoryFromDevfile(
            any(RemoteFactoryUrl.class), any(), anyMap(), anyBoolean()))
        .thenReturn(Optional.of(generateDevfileV2Factory()));

    // when
    bitbucketServerFactoryParametersResolver.createFactory(params);

    // then
    verify(urlFactoryBuilder)
        .createFactoryFromDevfile(
            any(BitbucketServerUrl.class),
            any(BitbucketServerAuthorizingFileContentProvider.class),
            anyMap(),
            eq(true));
  }
}
