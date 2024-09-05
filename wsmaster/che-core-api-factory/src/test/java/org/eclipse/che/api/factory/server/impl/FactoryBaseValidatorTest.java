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
package org.eclipse.che.api.factory.server.impl;

import static java.util.Collections.singletonList;
import static java.util.Objects.*;
import static org.eclipse.che.dto.server.DtoFactory.*;
import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.mockito.Mockito.lenient;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.factory.server.FactoryConstants;
import org.eclipse.che.api.factory.shared.dto.FactoryDevfileV2Dto;
import org.eclipse.che.api.factory.shared.dto.FactoryMetaDto;
import org.eclipse.che.api.factory.shared.dto.IdeActionDto;
import org.eclipse.che.api.factory.shared.dto.IdeDto;
import org.eclipse.che.api.factory.shared.dto.OnAppClosedDto;
import org.eclipse.che.api.factory.shared.dto.OnAppLoadedDto;
import org.eclipse.che.api.factory.shared.dto.OnProjectsLoadedDto;
import org.eclipse.che.api.factory.shared.dto.PoliciesDto;
import org.eclipse.che.api.user.server.model.impl.UserImpl;
import org.eclipse.che.api.user.server.spi.UserDao;
import org.eclipse.che.dto.server.DtoFactory;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(value = {MockitoTestNGListener.class})
public class FactoryBaseValidatorTest {
  private static final String VALID_REPOSITORY_URL = "https://github.com/codenvy/cloudide";
  private static final String VALID_PROJECT_PATH = "/cloudide";
  private static final String ID = "id";

  @Mock private UserDao userDao;

  private TesterFactoryBaseValidator validator;

  private FactoryDevfileV2Dto factory;

  @BeforeMethod
  public void setUp() throws ParseException, NotFoundException, ServerException {
    factory = newDto(FactoryDevfileV2Dto.class).withV("4.0");
    final UserImpl user = new UserImpl("userid", "email", "name");

    lenient().when(userDao.getById("userid")).thenReturn(user);
    validator = new TesterFactoryBaseValidator();
  }

  @DataProvider(name = "invalidProjectNamesProvider")
  public Object[][] invalidProjectNames() {
    return new Object[][] {
      {"-untitled"}, {"untitled->3"}, {"untitled__2%"}, {"untitled_!@#$%^&*()_+?><"}
    };
  }

  @Test(
      expectedExceptions = ApiException.class,
      expectedExceptionsMessageRegExp = FactoryConstants.ILLEGAL_FACTORY_BY_UNTIL_MESSAGE)
  public void shouldNotValidateIfUntilBeforeCurrentTime() throws ApiException {
    Long currentTime = new Date().getTime();
    factory.withPolicies(newDto(PoliciesDto.class).withUntil(currentTime - 10000L));

    validator.validateCurrentTimeBetweenSinceUntil(factory);
  }

  @Test
  public void shouldValidateIfCurrentTimeBetweenUntilSince() throws ApiException {
    Long currentTime = new Date().getTime();

    factory.withPolicies(
        newDto(PoliciesDto.class).withSince(currentTime - 10000L).withUntil(currentTime + 10000L));

    validator.validateCurrentTimeBetweenSinceUntil(factory);
  }

  @Test(
      expectedExceptions = ApiException.class,
      expectedExceptionsMessageRegExp = FactoryConstants.ILLEGAL_FACTORY_BY_SINCE_MESSAGE)
  public void shouldNotValidateIfUntilSinceAfterCurrentTime() throws ApiException {
    Long currentTime = new Date().getTime();
    factory.withPolicies(newDto(PoliciesDto.class).withSince(currentTime + 10000L));

    validator.validateCurrentTimeBetweenSinceUntil(factory);
  }

  @Test
  public void shouldValidateTrackedParamsIfOrgIdIsMissingButOnPremisesTrue() throws Exception {
    final DtoFactory dtoFactory = getInstance();
    FactoryDevfileV2Dto factory = dtoFactory.createDto(FactoryDevfileV2Dto.class);
    factory
        .withV("4.0")
        .withPolicies(
            dtoFactory
                .createDto(PoliciesDto.class)
                .withSince(System.currentTimeMillis() + 1_000_000)
                .withUntil(System.currentTimeMillis() + 10_000_000)
                .withReferer("codenvy.com"));
    validator = new TesterFactoryBaseValidator();
  }

  @Test(expectedExceptions = BadRequestException.class)
  public void shouldNotValidateOpenfileActionIfInWrongSectionOnAppClosed() throws Exception {
    // given
    validator = new TesterFactoryBaseValidator();
    List<IdeActionDto> actions = singletonList(newDto(IdeActionDto.class).withId("openFile"));
    IdeDto ide =
        newDto(IdeDto.class).withOnAppClosed(newDto(OnAppClosedDto.class).withActions(actions));
    FactoryMetaDto factoryWithAccountId = requireNonNull(getInstance().clone(factory)).withIde(ide);
    // when
    validator.validateProjectActions(factoryWithAccountId);
  }

  @Test(expectedExceptions = BadRequestException.class)
  public void shouldNotValidateFindReplaceActionIfInWrongSectionOnAppLoaded() throws Exception {
    // given
    validator = new TesterFactoryBaseValidator();
    List<IdeActionDto> actions = singletonList(newDto(IdeActionDto.class).withId("findReplace"));
    IdeDto ide =
        newDto(IdeDto.class).withOnAppLoaded(newDto(OnAppLoadedDto.class).withActions(actions));
    FactoryMetaDto factoryWithAccountId = requireNonNull(getInstance().clone(factory)).withIde(ide);
    // when
    validator.validateProjectActions(factoryWithAccountId);
  }

  @Test(expectedExceptions = BadRequestException.class)
  public void shouldNotValidateIfOpenfileActionInsufficientParams() throws Exception {
    // given
    validator = new TesterFactoryBaseValidator();
    List<IdeActionDto> actions = singletonList(newDto(IdeActionDto.class).withId("openFile"));
    IdeDto ide =
        newDto(IdeDto.class)
            .withOnProjectsLoaded(newDto(OnProjectsLoadedDto.class).withActions(actions));
    FactoryMetaDto factoryWithAccountId = requireNonNull(getInstance().clone(factory)).withIde(ide);
    // when
    validator.validateProjectActions(factoryWithAccountId);
  }

  @Test(expectedExceptions = BadRequestException.class)
  public void shouldNotValidateIfrunCommandActionInsufficientParams() throws Exception {
    // given
    validator = new TesterFactoryBaseValidator();
    List<IdeActionDto> actions = singletonList(newDto(IdeActionDto.class).withId("openFile"));
    IdeDto ide =
        newDto(IdeDto.class)
            .withOnProjectsLoaded(newDto(OnProjectsLoadedDto.class).withActions(actions));
    FactoryMetaDto factoryWithAccountId = requireNonNull(getInstance().clone(factory)).withIde(ide);
    // when
    validator.validateProjectActions(factoryWithAccountId);
  }

  @Test(expectedExceptions = BadRequestException.class)
  public void shouldNotValidateIfOpenWelcomePageActionInsufficientParams() throws Exception {
    // given
    validator = new TesterFactoryBaseValidator();
    List<IdeActionDto> actions =
        singletonList(newDto(IdeActionDto.class).withId("openWelcomePage"));
    IdeDto ide =
        newDto(IdeDto.class).withOnAppLoaded((newDto(OnAppLoadedDto.class).withActions(actions)));
    FactoryMetaDto factoryWithAccountId = requireNonNull(getInstance().clone(factory)).withIde(ide);
    // when
    validator.validateProjectActions(factoryWithAccountId);
  }

  @Test(expectedExceptions = BadRequestException.class)
  public void shouldNotValidateIfFindReplaceActionInsufficientParams() throws Exception {
    // given
    validator = new TesterFactoryBaseValidator();
    Map<String, String> params = new HashMap<>();
    params.put("in", "pom.xml");
    // find is missing!
    params.put("replace", "123");
    List<IdeActionDto> actions =
        singletonList(newDto(IdeActionDto.class).withId("findReplace").withProperties(params));
    IdeDto ide =
        newDto(IdeDto.class)
            .withOnProjectsLoaded(newDto(OnProjectsLoadedDto.class).withActions(actions));
    FactoryMetaDto factoryWithAccountId = requireNonNull(getInstance().clone(factory)).withIde(ide);
    // when
    validator.validateProjectActions(factoryWithAccountId);
  }

  @Test
  public void shouldValidateFindReplaceAction() throws Exception {
    // given
    validator = new TesterFactoryBaseValidator();
    Map<String, String> params = new HashMap<>();
    params.put("in", "pom.xml");
    params.put("find", "123");
    params.put("replace", "456");
    List<IdeActionDto> actions =
        singletonList(newDto(IdeActionDto.class).withId("findReplace").withProperties(params));
    IdeDto ide =
        newDto(IdeDto.class)
            .withOnProjectsLoaded(newDto(OnProjectsLoadedDto.class).withActions(actions));
    FactoryMetaDto factoryWithAccountId = requireNonNull(getInstance().clone(factory)).withIde(ide);
    // when
    validator.validateProjectActions(factoryWithAccountId);
  }

  @Test
  public void shouldValidateOpenfileAction() throws Exception {
    // given
    validator = new TesterFactoryBaseValidator();
    Map<String, String> params = new HashMap<>();
    params.put("file", "pom.xml");
    List<IdeActionDto> actions =
        singletonList(newDto(IdeActionDto.class).withId("openFile").withProperties(params));
    IdeDto ide =
        newDto(IdeDto.class)
            .withOnProjectsLoaded(newDto(OnProjectsLoadedDto.class).withActions(actions));
    FactoryMetaDto factoryWithAccountId = requireNonNull(getInstance().clone(factory)).withIde(ide);
    // when
    validator.validateProjectActions(factoryWithAccountId);
  }

  @DataProvider(name = "trackedFactoryParameterWithoutValidAccountId")
  public Object[][] trackedFactoryParameterWithoutValidAccountId()
      throws URISyntaxException, IOException, NoSuchMethodException {
    return new Object[][] {
      {
        newDto(FactoryMetaDto.class)
            .withV("4.0")
            .withIde(
                newDto(IdeDto.class)
                    .withOnAppLoaded(
                        newDto(OnAppLoadedDto.class)
                            .withActions(
                                singletonList(
                                    newDto(IdeActionDto.class)
                                        .withId("openWelcomePage")
                                        .withProperties(
                                            ImmutableMap.<String, String>builder()
                                                .put("authenticatedTitle", "title")
                                                .put("authenticatedIconUrl", "url")
                                                .put("authenticatedContentUrl", "url")
                                                .put("nonAuthenticatedTitle", "title")
                                                .put("nonAuthenticatedIconUrl", "url")
                                                .put("nonAuthenticatedContentUrl", "url")
                                                .build())))))
      },
      {
        newDto(FactoryMetaDto.class)
            .withV("4.0")
            .withPolicies(newDto(PoliciesDto.class).withSince(10000L))
      },
      {
        newDto(FactoryMetaDto.class)
            .withV("4.0")
            .withPolicies(newDto(PoliciesDto.class).withUntil(10000L))
      },
      {
        newDto(FactoryMetaDto.class)
            .withV("4.0")
            .withPolicies(newDto(PoliciesDto.class).withReferer("host"))
      }
    };
  }
}
