/*
 * Copyright (c) 2012-2023 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.factory.server;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static java.lang.String.valueOf;
import static java.util.Collections.singletonMap;
import static org.eclipse.che.api.factory.server.FactoryResolverPriority.DEFAULT;
import static org.eclipse.che.api.factory.server.FactoryResolverPriority.HIGHEST;
import static org.eclipse.che.api.factory.server.FactoryResolverPriority.LOWEST;
import static org.eclipse.che.api.factory.server.FactoryService.VALIDATE_QUERY_PARAMETER;
import static org.eclipse.che.api.factory.shared.Constants.CURRENT_VERSION;
import static org.eclipse.che.api.factory.shared.Constants.URL_PARAMETER_NAME;
import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.everrest.assured.JettyHttpServer.ADMIN_USER_NAME;
import static org.everrest.assured.JettyHttpServer.ADMIN_USER_PASSWORD;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.model.user.User;
import org.eclipse.che.api.core.rest.ApiExceptionMapper;
import org.eclipse.che.api.core.rest.shared.dto.ServiceError;
import org.eclipse.che.api.factory.server.FactoryService.FactoryParametersResolverHolder;
import org.eclipse.che.api.factory.server.builder.FactoryBuilder;
import org.eclipse.che.api.factory.server.impl.SourceStorageParametersValidator;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl;
import org.eclipse.che.api.factory.shared.dto.FactoryDto;
import org.eclipse.che.api.user.server.PreferenceManager;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.api.user.server.model.impl.UserImpl;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.eclipse.che.dto.server.DtoFactory;
import org.everrest.assured.EverrestJetty;
import org.everrest.core.Filter;
import org.everrest.core.GenericContainerRequest;
import org.everrest.core.RequestFilter;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Tests for {@link FactoryService}.
 *
 * @author Anton Korneta
 */
@Listeners(value = {EverrestJetty.class, MockitoTestNGListener.class})
public class FactoryServiceTest {

  private static final String SERVICE_PATH = "/factory";
  private static final String FACTORY_ID = "correctFactoryId";
  private static final String FACTORY_NAME = "factory";
  private static final String USER_ID = "userId";
  private static final String USER_EMAIL = "email";
  private static final String WORKSPACE_NAME = "workspace";
  private static final String PROJECT_SOURCE_TYPE = "git";
  private static final String PROJECT_SOURCE_LOCATION =
      "https://github.com/codenvy/platform-api.git";

  private static final DtoFactory DTO = DtoFactory.getInstance();

  @Mock private FactoryAcceptValidator acceptValidator;
  @Mock private PreferenceManager preferenceManager;
  @Mock private UserManager userManager;
  @Mock private AdditionalFilenamesProvider additionalFilenamesProvider;
  @Mock private RawDevfileUrlFactoryParameterResolver rawDevfileUrlFactoryParameterResolver;
  @Mock private PersonalAccessTokenManager personalAccessTokenManager;

  @InjectMocks private FactoryParametersResolverHolder factoryParametersResolverHolder;
  private Set<FactoryParametersResolver> specificFactoryParametersResolvers;

  private FactoryBuilder factoryBuilderSpy;

  private User user;

  private FactoryService service;

  @SuppressWarnings("unused")
  private ApiExceptionMapper apiExceptionMapper;

  @SuppressWarnings("unused")
  private EnvironmentFilter environmentFilter;

  @BeforeMethod
  public void setUp() throws Exception {
    specificFactoryParametersResolvers = new HashSet<>();
    Field parametersResolvers =
        FactoryParametersResolverHolder.class.getDeclaredField(
            "specificFactoryParametersResolvers");
    parametersResolvers.setAccessible(true);
    parametersResolvers.set(factoryParametersResolverHolder, specificFactoryParametersResolvers);
    specificFactoryParametersResolvers.add(rawDevfileUrlFactoryParameterResolver);
    factoryBuilderSpy = spy(new FactoryBuilder(new SourceStorageParametersValidator()));
    lenient().doNothing().when(factoryBuilderSpy).checkValid(any(FactoryDto.class));
    lenient().doNothing().when(factoryBuilderSpy).checkValid(any(FactoryDto.class), anyBoolean());
    user = new UserImpl(USER_ID, USER_EMAIL, ADMIN_USER_NAME);
    lenient()
        .when(preferenceManager.find(USER_ID))
        .thenReturn(ImmutableMap.of("preference", "value"));
    service =
        new FactoryService(
            acceptValidator,
            factoryParametersResolverHolder,
            additionalFilenamesProvider,
            personalAccessTokenManager);
  }

  @Filter
  public static class EnvironmentFilter implements RequestFilter {
    @Override
    public void doFilter(GenericContainerRequest request) {
      EnvironmentContext context = EnvironmentContext.getCurrent();
      context.setSubject(new SubjectImpl(ADMIN_USER_NAME, USER_ID, ADMIN_USER_PASSWORD, false));
    }
  }

  @Test
  public void shouldThrowBadRequestWhenNoURLParameterGiven() throws Exception {
    final FactoryParametersResolverHolder dummyHolder = spy(factoryParametersResolverHolder);
    doReturn(rawDevfileUrlFactoryParameterResolver)
        .when(dummyHolder)
        .getFactoryParametersResolver(anyMap());
    // service instance with dummy holder
    service =
        new FactoryService(
            acceptValidator, dummyHolder, additionalFilenamesProvider, personalAccessTokenManager);

    // when
    final Map<String, String> map = new HashMap<>();
    final Response response =
        given()
            .contentType(ContentType.JSON)
            .when()
            .body(map)
            .queryParam(VALIDATE_QUERY_PARAMETER, valueOf(true))
            .post(SERVICE_PATH + "/resolver");

    assertEquals(response.getStatusCode(), 400);
    assertEquals(
        DTO.createDtoFromJson(response.getBody().asString(), ServiceError.class).getMessage(),
        "Cannot build factory with any of the provided parameters. Please check parameters correctness, and resend query.");
  }

  @Test
  public void checkValidateResolver() throws Exception {
    final FactoryParametersResolverHolder dummyHolder = spy(factoryParametersResolverHolder);
    doReturn(rawDevfileUrlFactoryParameterResolver)
        .when(dummyHolder)
        .getFactoryParametersResolver(anyMap());
    // service instance with dummy holder
    service =
        new FactoryService(
            acceptValidator, dummyHolder, additionalFilenamesProvider, personalAccessTokenManager);

    // invalid factory
    final String invalidFactoryMessage = "invalid factory";
    doThrow(new BadRequestException(invalidFactoryMessage))
        .when(acceptValidator)
        .validateOnAccept(any());

    // create factory
    final FactoryDto expectFactory =
        newDto(FactoryDto.class).withV(CURRENT_VERSION).withName("matchingResolverFactory");

    // accept resolver
    when(rawDevfileUrlFactoryParameterResolver.createFactory(anyMap())).thenReturn(expectFactory);

    // when
    final Map<String, String> map = new HashMap<>();
    final Response response =
        given()
            .contentType(ContentType.JSON)
            .when()
            .body(map)
            .queryParam(VALIDATE_QUERY_PARAMETER, valueOf(true))
            .post(SERVICE_PATH + "/resolver");

    // then check we have a bad request
    assertEquals(response.getStatusCode(), BAD_REQUEST.getStatusCode());
    assertTrue(response.getBody().asString().contains(invalidFactoryMessage));

    // check we call resolvers
    dummyHolder.getFactoryParametersResolver(anyMap());
    verify(rawDevfileUrlFactoryParameterResolver).createFactory(anyMap());

    // check we call validator
    verify(acceptValidator).validateOnAccept(any());
  }

  @Test
  public void checkRefreshToken() throws Exception {
    // given
    final FactoryParametersResolverHolder dummyHolder = spy(factoryParametersResolverHolder);
    FactoryParametersResolver factoryParametersResolver = mock(FactoryParametersResolver.class);
    RemoteFactoryUrl remoteFactoryUrl = mock(RemoteFactoryUrl.class);
    when(factoryParametersResolver.parseFactoryUrl(eq("someUrl"))).thenReturn(remoteFactoryUrl);
    when(remoteFactoryUrl.getHostName()).thenReturn("hostName");
    doReturn(factoryParametersResolver).when(dummyHolder).getFactoryParametersResolver(anyMap());
    service =
        new FactoryService(
            acceptValidator, dummyHolder, additionalFilenamesProvider, personalAccessTokenManager);

    // when
    given()
        .contentType(ContentType.JSON)
        .when()
        .queryParam("url", "someUrl")
        .post(SERVICE_PATH + "/token/refresh");

    // then
    verify(personalAccessTokenManager).getAndStore(eq("hostName"));
  }

  @Test
  public void shouldThrowBadRequestWhenRefreshTokenWithoutUrl() throws Exception {
    service =
        new FactoryService(
            acceptValidator,
            factoryParametersResolverHolder,
            additionalFilenamesProvider,
            personalAccessTokenManager);

    // when
    final Response response =
        given().contentType(ContentType.JSON).when().post(SERVICE_PATH + "/token/refresh");

    assertEquals(response.getStatusCode(), 400);
    assertEquals(
        DTO.createDtoFromJson(response.getBody().asString(), ServiceError.class).getMessage(),
        "Factory url required");
  }

  @Test
  public void shouldReturnDefaultFactoryParameterResolver() throws Exception {
    // given
    Map<String, String> params = singletonMap(URL_PARAMETER_NAME, "https://host/path/devfile.yaml");
    when(rawDevfileUrlFactoryParameterResolver.accept(eq(params))).thenReturn(true);

    // when
    FactoryParametersResolver factoryParametersResolver =
        factoryParametersResolverHolder.getFactoryParametersResolver(params);

    // then
    assertTrue(
        factoryParametersResolver
            .getClass()
            .getName()
            .startsWith(RawDevfileUrlFactoryParameterResolver.class.getName()));
  }

  @Test
  public void shouldReturnTopPriorityFactoryParameterResolverOverLowPriority() throws Exception {
    // given
    Map<String, String> params = singletonMap(URL_PARAMETER_NAME, "https://host/path/devfile.yaml");
    specificFactoryParametersResolvers.clear();
    FactoryParametersResolver topPriorityResolver = mock(FactoryParametersResolver.class);
    FactoryParametersResolver lowPriorityResolver = mock(FactoryParametersResolver.class);
    when(topPriorityResolver.accept(eq(params))).thenReturn(true);
    when(lowPriorityResolver.accept(eq(params))).thenReturn(true);
    when(topPriorityResolver.priority()).thenReturn(HIGHEST);
    when(lowPriorityResolver.priority()).thenReturn(LOWEST);
    specificFactoryParametersResolvers.add(topPriorityResolver);
    specificFactoryParametersResolvers.add(lowPriorityResolver);

    // when
    FactoryParametersResolver factoryParametersResolver =
        factoryParametersResolverHolder.getFactoryParametersResolver(params);

    // then
    assertEquals(factoryParametersResolver, topPriorityResolver);
  }

  @Test
  public void shouldReturnTopPriorityFactoryParameterResolverOverDefaultPriority()
      throws Exception {
    // given
    Map<String, String> params = singletonMap(URL_PARAMETER_NAME, "https://host/path/devfile.yaml");
    specificFactoryParametersResolvers.clear();
    FactoryParametersResolver topPriorityResolver = mock(FactoryParametersResolver.class);
    FactoryParametersResolver defaultPriorityResolver = mock(FactoryParametersResolver.class);
    when(topPriorityResolver.accept(eq(params))).thenReturn(true);
    when(defaultPriorityResolver.accept(eq(params))).thenReturn(true);
    when(topPriorityResolver.priority()).thenReturn(HIGHEST);
    when(defaultPriorityResolver.priority()).thenReturn(DEFAULT);
    specificFactoryParametersResolvers.add(topPriorityResolver);
    specificFactoryParametersResolvers.add(defaultPriorityResolver);

    // when
    FactoryParametersResolver factoryParametersResolver =
        factoryParametersResolverHolder.getFactoryParametersResolver(params);

    // then
    assertEquals(factoryParametersResolver, topPriorityResolver);
  }
}
