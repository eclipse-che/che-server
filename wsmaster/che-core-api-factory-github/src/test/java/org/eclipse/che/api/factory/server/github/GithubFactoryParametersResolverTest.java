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
package org.eclipse.che.api.factory.server.github;

import static java.util.Collections.singletonMap;
import static org.eclipse.che.api.factory.shared.Constants.CURRENT_VERSION;
import static org.eclipse.che.api.factory.shared.Constants.URL_PARAMETER_NAME;
import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.eclipse.che.security.oauth1.OAuthAuthenticationService.ERROR_QUERY_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

/**
 * Validate operations performed by the Github Factory service
 *
 * @author Florent Benoit
 */
@Listeners(MockitoTestNGListener.class)
public class GithubFactoryParametersResolverTest {

  @Mock private URLFetcher urlFetcher;

  @Mock private DevfileFilenamesProvider devfileFilenamesProvider;

  /** Parser which will allow to check validity of URLs and create objects. */
  private GithubURLParser githubUrlParser;

  private GithubApiClient githubApiClient;

  /** Converter allowing to convert github URL to other objects. */
  @Spy
  private GithubSourceStorageBuilder githubSourceStorageBuilder = new GithubSourceStorageBuilder();

  /** Parser which will allow to check validity of URLs and create objects. */
  private URLFactoryBuilder urlFactoryBuilder;

  @Mock private PersonalAccessTokenManager personalAccessTokenManager;
  @Mock private AuthorisationRequestManager authorisationRequestManager;

  /**
   * Capturing the location parameter when calling {@link
   * URLFactoryBuilder#createFactoryFromDevfile(RemoteFactoryUrl, FileContentProvider, Map,
   * boolean)}
   */
  @Captor private ArgumentCaptor<RemoteFactoryUrl> factoryUrlArgumentCaptor;

  /** Instance of resolver that will be tested. */
  private AbstractGithubFactoryParametersResolver abstractGithubFactoryParametersResolver;

  @BeforeMethod
  protected void init() throws Exception {
    this.githubApiClient = mock(GithubApiClient.class);
    this.urlFactoryBuilder = mock(URLFactoryBuilder.class);

    githubUrlParser =
        new GithubURLParser(
            personalAccessTokenManager, devfileFilenamesProvider, githubApiClient, null, false);

    abstractGithubFactoryParametersResolver =
        new GithubFactoryParametersResolver(
            githubUrlParser,
            urlFetcher,
            githubSourceStorageBuilder,
            authorisationRequestManager,
            urlFactoryBuilder,
            personalAccessTokenManager);
    assertNotNull(this.abstractGithubFactoryParametersResolver);
  }

  /** Check missing parameter name can't be accepted by this resolver */
  @Test
  public void checkMissingParameter() {
    Map<String, String> parameters = singletonMap("foo", "this is a foo bar");
    boolean accept = abstractGithubFactoryParametersResolver.accept(parameters);
    // shouldn't be accepted
    assertFalse(accept);
  }

  /** Check url which is not a github url can't be accepted by this resolver */
  @Test
  public void checkInvalidAcceptUrl() {
    Map<String, String> parameters = singletonMap(URL_PARAMETER_NAME, "http://www.eclipse.org/che");
    boolean accept = abstractGithubFactoryParametersResolver.accept(parameters);
    // shouldn't be accepted
    assertFalse(accept);
  }

  /** Check github url will be be accepted by this resolver */
  @Test
  public void checkValidAcceptUrl() {
    Map<String, String> parameters =
        singletonMap(URL_PARAMETER_NAME, "https://github.com/codenvy/codenvy.git");
    boolean accept = abstractGithubFactoryParametersResolver.accept(parameters);
    // shouldn't be accepted
    assertTrue(accept);
  }

  @Test
  public void shouldGenerateDevfileForFactoryWithNoDevfile() throws Exception {

    String githubUrl = "https://github.com/eclipse/che";

    when(githubApiClient.isConnected(eq("https://github.com"))).thenReturn(true);
    when(githubApiClient.getLatestCommit(anyString(), anyString(), anyString(), any()))
        .thenReturn(new GithubCommit().withSha("test-sha"));

    Map<String, String> params = ImmutableMap.of(URL_PARAMETER_NAME, githubUrl);
    // when
    FactoryDevfileV2Dto factory =
        (FactoryDevfileV2Dto) abstractGithubFactoryParametersResolver.createFactory(params);
    // then
    ScmInfoDto scmInfo = factory.getScmInfo();
    assertEquals(scmInfo.getRepositoryUrl(), githubUrl + ".git");
    assertEquals(scmInfo.getBranch(), null);
  }

  @Test
  public void shouldSkipAuthenticationWhenAccessDenied() throws Exception {
    // given
    when(githubApiClient.isConnected(eq("https://github.com"))).thenReturn(true);
    when(githubApiClient.getLatestCommit(anyString(), anyString(), anyString(), any()))
        .thenReturn(new GithubCommit().withSha("test-sha"));

    // when
    Map<String, String> params =
        ImmutableMap.of(
            URL_PARAMETER_NAME,
            "https://github.com/eclipse/che",
            ERROR_QUERY_NAME,
            "access_denied");
    abstractGithubFactoryParametersResolver.createFactory(params);
    // then
    verify(urlFactoryBuilder)
        .createFactoryFromDevfile(
            any(RemoteFactoryUrl.class), any(FileContentProvider.class), anyMap(), eq(true));
  }

  @Test
  public void shouldNotSkipAuthenticationWhenNoErrorParameterPassed() throws Exception {
    // given
    when(githubApiClient.isConnected(eq("https://github.com"))).thenReturn(true);
    when(githubApiClient.getLatestCommit(anyString(), anyString(), anyString(), any()))
        .thenReturn(new GithubCommit().withSha("test-sha"));

    // when
    abstractGithubFactoryParametersResolver.createFactory(
        ImmutableMap.of(URL_PARAMETER_NAME, "https://github.com/eclipse/che"));
    // then
    verify(urlFactoryBuilder)
        .createFactoryFromDevfile(
            any(RemoteFactoryUrl.class), any(FileContentProvider.class), anyMap(), eq(false));
  }

  @Test
  public void shouldSetScmInfoIntoDevfileV2() throws Exception {

    String githubUrl = "https://github.com/eclipse/che/tree/foobar";

    FactoryDevfileV2Dto computedFactory = generateDevfileV2Factory();

    when(urlFactoryBuilder.createFactoryFromDevfile(
            any(RemoteFactoryUrl.class), any(), anyMap(), anyBoolean()))
        .thenReturn(Optional.of(computedFactory));
    when(githubApiClient.isConnected(eq("https://github.com"))).thenReturn(true);
    when(githubApiClient.getLatestCommit(anyString(), anyString(), anyString(), any()))
        .thenReturn(new GithubCommit().withSha("test-sha"));

    Map<String, String> params = ImmutableMap.of(URL_PARAMETER_NAME, githubUrl);
    // when
    FactoryDevfileV2Dto factory =
        (FactoryDevfileV2Dto) abstractGithubFactoryParametersResolver.createFactory(params);
    // then
    ScmInfo scmInfo = factory.getScmInfo();
    assertNotNull(scmInfo);
    assertEquals(scmInfo.getScmProviderName(), "github");
    assertEquals(scmInfo.getRepositoryUrl(), "https://github.com/eclipse/che.git");
    assertEquals(scmInfo.getBranch(), "foobar");
  }

  @Test
  public void shouldCreateFactoryWithoutAuthentication() throws ApiException {
    // given
    String githubUrl = "https://github.com/user/repo.git";
    Map<String, String> params =
        ImmutableMap.of(URL_PARAMETER_NAME, githubUrl, ERROR_QUERY_NAME, "access_denied");
    when(urlFactoryBuilder.createFactoryFromDevfile(
            any(RemoteFactoryUrl.class), any(), anyMap(), anyBoolean()))
        .thenReturn(Optional.of(generateDevfileV2Factory()));

    // when
    abstractGithubFactoryParametersResolver.createFactory(params);

    // then
    verify(urlFactoryBuilder)
        .createFactoryFromDevfile(
            any(GithubUrl.class),
            any(GithubAuthorizingFileContentProvider.class),
            anyMap(),
            eq(true));
  }

  @Test
  public void shouldParseFactoryUrlWithAuthentication() throws Exception {
    // given
    githubUrlParser = mock(GithubURLParser.class);

    abstractGithubFactoryParametersResolver =
        new GithubFactoryParametersResolver(
            githubUrlParser,
            urlFetcher,
            githubSourceStorageBuilder,
            authorisationRequestManager,
            urlFactoryBuilder,
            personalAccessTokenManager);
    when(authorisationRequestManager.isStored(eq("github"))).thenReturn(true);
    // when
    abstractGithubFactoryParametersResolver.parseFactoryUrl("url");
    // then
    verify(githubUrlParser).parseWithoutAuthentication("url");
    verify(githubUrlParser, never()).parse("url");
  }

  @Test
  public void shouldParseFactoryUrlWithOutAuthentication() throws Exception {
    // given
    githubUrlParser = mock(GithubURLParser.class);

    abstractGithubFactoryParametersResolver =
        new GithubFactoryParametersResolver(
            githubUrlParser,
            urlFetcher,
            githubSourceStorageBuilder,
            authorisationRequestManager,
            urlFactoryBuilder,
            personalAccessTokenManager);
    when(authorisationRequestManager.isStored(eq("github"))).thenReturn(false);
    // when
    abstractGithubFactoryParametersResolver.parseFactoryUrl("url");
    // then
    verify(githubUrlParser).parse("url");
    verify(githubUrlParser, never()).parseWithoutAuthentication("url");
  }

  private FactoryDevfileV2Dto generateDevfileV2Factory() {
    return newDto(FactoryDevfileV2Dto.class)
        .withV(CURRENT_VERSION)
        .withSource("repo")
        .withDevfile(Map.of("schemaVersion", "2.0.0"));
  }
}
