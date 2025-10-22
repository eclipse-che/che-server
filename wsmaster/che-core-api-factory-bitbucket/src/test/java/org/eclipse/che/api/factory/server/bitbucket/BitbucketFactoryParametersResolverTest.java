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
import org.eclipse.che.api.workspace.server.devfile.FileContentProvider;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/** Validate operations performed by the Bitbucket Factory service */
@Listeners(MockitoTestNGListener.class)
public class BitbucketFactoryParametersResolverTest {

  @Mock private URLFetcher urlFetcher;

  @Mock private DevfileFilenamesProvider devfileFilenamesProvider;

  @Mock private AuthorisationRequestManager authorisationRequestManager;

  /** Parser which will allow to check validity of URLs and create objects. */
  private BitbucketURLParser bitbucketURLParser;

  /** Converter allowing to convert bitbucket URL to other objects. */
  @Spy
  private BitbucketSourceStorageBuilder bitbucketSourceStorageBuilder =
      new BitbucketSourceStorageBuilder();

  /** Parser which will allow to check validity of URLs and create objects. */
  @Mock private URLFactoryBuilder urlFactoryBuilder;

  @Mock private PersonalAccessTokenManager personalAccessTokenManager;
  @Mock private BitbucketApiClient bitbucketApiClient;

  /**
   * Capturing the location parameter when calling {@link
   * URLFactoryBuilder#createFactoryFromDevfile(RemoteFactoryUrl, FileContentProvider, Map,
   * boolean)}
   */
  @Captor private ArgumentCaptor<RemoteFactoryUrl> factoryUrlArgumentCaptor;

  /** Instance of resolver that will be tested. */
  private BitbucketFactoryParametersResolver bitbucketFactoryParametersResolver;

  @BeforeMethod
  protected void init() {
    bitbucketURLParser = new BitbucketURLParser(devfileFilenamesProvider);
    assertNotNull(this.bitbucketURLParser);
    bitbucketFactoryParametersResolver =
        new BitbucketFactoryParametersResolver(
            bitbucketURLParser,
            urlFetcher,
            bitbucketSourceStorageBuilder,
            urlFactoryBuilder,
            personalAccessTokenManager,
            bitbucketApiClient,
            authorisationRequestManager);
    assertNotNull(this.bitbucketFactoryParametersResolver);
  }

  /** Check missing parameter name can't be accepted by this resolver */
  @Test
  public void checkMissingParameter() {
    Map<String, String> parameters = singletonMap("foo", "this is a foo bar");
    boolean accept = bitbucketFactoryParametersResolver.accept(parameters);
    // shouldn't be accepted
    assertFalse(accept);
  }

  /** Check url which is not a bitbucket url can't be accepted by this resolver */
  @Test
  public void checkInvalidAcceptUrl() {
    Map<String, String> parameters = singletonMap(URL_PARAMETER_NAME, "http://www.eclipse.org/che");
    boolean accept = bitbucketFactoryParametersResolver.accept(parameters);
    // shouldn't be accepted
    assertFalse(accept);
  }

  /** Check bitbucket url will be accepted by this resolver */
  @Test
  public void checkValidAcceptUrl() {
    Map<String, String> parameters =
        singletonMap(URL_PARAMETER_NAME, "https://bitbucket.org/eclipse/che.git");
    boolean accept = bitbucketFactoryParametersResolver.accept(parameters);
    // shouldn't be accepted
    assertTrue(accept);
  }

  @Test
  public void shouldGenerateDevfileForFactoryWithNoDevfile() throws Exception {

    String bitbucketUrl = "https://bitbucket.org/eclipse/che";

    when(urlFactoryBuilder.createFactoryFromDevfile(
            any(RemoteFactoryUrl.class), any(), anyMap(), anyBoolean()))
        .thenReturn(Optional.empty());
    Map<String, String> params = ImmutableMap.of(URL_PARAMETER_NAME, bitbucketUrl);
    // when
    FactoryDevfileV2Dto factory =
        (FactoryDevfileV2Dto) bitbucketFactoryParametersResolver.createFactory(params);
    // then
    ScmInfoDto scmInfo = factory.getScmInfo();
    assertEquals(scmInfo.getRepositoryUrl(), bitbucketUrl + ".git");
    assertEquals(scmInfo.getBranch(), null);
  }

  @Test
  public void shouldSetScmInfoIntoDevfileV2() throws Exception {

    String bitbucketUrl = "https://bitbucket.org/eclipse/che/src/foobar";

    FactoryDevfileV2Dto computedFactory = generateDevfileV2Factory();

    when(urlFactoryBuilder.createFactoryFromDevfile(
            any(RemoteFactoryUrl.class), any(), anyMap(), anyBoolean()))
        .thenReturn(Optional.of(computedFactory));

    Map<String, String> params = ImmutableMap.of(URL_PARAMETER_NAME, bitbucketUrl);
    // when
    FactoryDevfileV2Dto factory =
        (FactoryDevfileV2Dto) bitbucketFactoryParametersResolver.createFactory(params);
    // then
    ScmInfo scmInfo = factory.getScmInfo();
    assertNotNull(scmInfo);
    assertEquals(scmInfo.getScmProviderName(), "bitbucket");
    assertEquals(scmInfo.getRepositoryUrl(), "https://bitbucket.org/eclipse/che.git");
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
    String bitbucketUrl = "https://bitbucket.org/user/repo.git";
    Map<String, String> params =
        ImmutableMap.of(URL_PARAMETER_NAME, bitbucketUrl, ERROR_QUERY_NAME, "access_denied");
    when(urlFactoryBuilder.createFactoryFromDevfile(
            any(RemoteFactoryUrl.class), any(), anyMap(), anyBoolean()))
        .thenReturn(Optional.of(generateDevfileV2Factory()));

    // when
    bitbucketFactoryParametersResolver.createFactory(params);

    // then
    verify(urlFactoryBuilder)
        .createFactoryFromDevfile(
            any(BitbucketUrl.class),
            any(BitbucketAuthorizingFileContentProvider.class),
            anyMap(),
            eq(true));
  }
}
