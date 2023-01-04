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
package org.eclipse.che.api.devfile.server;

import static io.restassured.RestAssured.given;
import static org.eclipse.che.api.devfile.server.TestObjectGenerator.TEST_SUBJECT;
import static org.eclipse.che.api.workspace.server.devfile.Constants.CURRENT_API_VERSION;
import static org.eclipse.che.api.workspace.server.devfile.Constants.SUPPORTED_VERSIONS;
import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.everrest.assured.JettyHttpServer.ADMIN_USER_NAME;
import static org.everrest.assured.JettyHttpServer.ADMIN_USER_PASSWORD;
import static org.everrest.assured.JettyHttpServer.SECURE_PATH;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.testng.Assert.assertEquals;

import io.restassured.response.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.core.rest.ApiExceptionMapper;
import org.eclipse.che.api.core.rest.CheJsonProvider;
import org.eclipse.che.api.core.rest.ServiceContext;
import org.eclipse.che.api.core.rest.WebApplicationExceptionMapper;
import org.eclipse.che.api.devfile.server.spi.UserDevfileDao;
import org.eclipse.che.api.devfile.shared.dto.UserDevfileDto;
import org.eclipse.che.api.workspace.server.devfile.DevfileEntityProvider;
import org.eclipse.che.api.workspace.server.devfile.DevfileParser;
import org.eclipse.che.api.workspace.server.devfile.DevfileVersionDetector;
import org.eclipse.che.api.workspace.server.devfile.schema.DevfileSchemaProvider;
import org.eclipse.che.api.workspace.server.devfile.validator.DevfileIntegrityValidator;
import org.eclipse.che.api.workspace.server.devfile.validator.DevfileSchemaValidator;
import org.eclipse.che.api.workspace.shared.dto.devfile.DevfileDto;
import org.eclipse.che.api.workspace.shared.dto.devfile.MetadataDto;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.dto.server.DtoFactory;
import org.everrest.assured.EverrestJetty;
import org.everrest.core.Filter;
import org.everrest.core.GenericContainerRequest;
import org.everrest.core.RequestFilter;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners({EverrestJetty.class, MockitoTestNGListener.class})
public class DevfileServiceTest {

  @SuppressWarnings("unused") // is declared for deploying by everrest-assured
  ApiExceptionMapper exceptionMapper = new ApiExceptionMapper();

  WebApplicationExceptionMapper exceptionMapper2 = new WebApplicationExceptionMapper();

  private DevfileSchemaProvider schemaProvider = new DevfileSchemaProvider();

  private static final EnvironmentFilter FILTER = new EnvironmentFilter();

  private DevfileParser devfileParser =
      new DevfileParser(
          new DevfileSchemaValidator(new DevfileSchemaProvider(), new DevfileVersionDetector()),
          new DevfileIntegrityValidator(Collections.emptyMap()));
  DevfileEntityProvider devfileEntityProvider = new DevfileEntityProvider(devfileParser);
  UserDevfileEntityProvider userDevfileEntityProvider =
      new UserDevfileEntityProvider(devfileParser);
  private CheJsonProvider jsonProvider = new CheJsonProvider(new HashSet<>());

  @Mock UserDevfileDao userDevfileDao;
  @Mock UserDevfileManager userDevfileManager;
  @Mock EventService eventService;
  @Mock DevfileServiceLinksInjector linksInjector;

  DevfileService userDevfileService;

  @Test
  public void shouldRetrieveSchema() throws Exception {
    final Response response =
        given()
            .auth()
            .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
            .when()
            .get(SECURE_PATH + "/devfile");

    assertEquals(response.getStatusCode(), 200);
    assertEquals(
        response.getBody().asString(), schemaProvider.getSchemaContent(CURRENT_API_VERSION));
  }

  @Test
  public void shouldReturn404WhenSchemaNotFound() {
    final Response response =
        given()
            .auth()
            .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
            .when()
            .get(SECURE_PATH + "/devfile?version=1.2.3.4.5");

    assertEquals(response.getStatusCode(), 404);
  }

  @Test(dataProvider = "schemaVersions")
  public void shouldRetrieveSchemaVersion(String version) throws Exception {
    final Response response =
        given()
            .auth()
            .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
            .when()
            .get(SECURE_PATH + "/devfile?version=" + version);

    assertEquals(response.getStatusCode(), 200);
    assertEquals(response.getBody().asString(), schemaProvider.getSchemaContent(version));
  }

  @DataProvider
  public static Object[][] schemaVersions() {
    Object[][] versions = new Object[SUPPORTED_VERSIONS.size()][];
    for (int i = 0; i < SUPPORTED_VERSIONS.size(); i++) {
      versions[i] = new Object[] {SUPPORTED_VERSIONS.get(i)};
    }

    return versions;
  }

  @BeforeMethod
  public void setup() {
    this.userDevfileService = new DevfileService(schemaProvider);
    lenient()
        .when(linksInjector.injectLinks(any(UserDevfileDto.class), any(ServiceContext.class)))
        .thenAnswer((Answer<UserDevfileDto>) invocation -> invocation.getArgument(0));
  }

  @DataProvider
  public Object[][] validUserDevfiles() {
    return new Object[][] {
      {
        newDto(UserDevfileDto.class)
            .withName("My devfile")
            .withDescription("Devfile description")
            .withDevfile(
                newDto(DevfileDto.class)
                    .withApiVersion("1.0.0")
                    .withMetadata(newDto(MetadataDto.class).withName("name")))
      },
      {
        newDto(UserDevfileDto.class)
            .withName(null)
            .withDescription("Devfile description")
            .withDevfile(
                newDto(DevfileDto.class)
                    .withApiVersion("1.0.0")
                    .withMetadata(newDto(MetadataDto.class).withName("name")))
      },
      {
        newDto(UserDevfileDto.class)
            .withName("My devfile")
            .withDevfile(
                newDto(DevfileDto.class)
                    .withApiVersion("1.0.0")
                    .withMetadata(newDto(MetadataDto.class).withName("name")))
      },
      {
        newDto(UserDevfileDto.class)
            .withName("My devfile")
            .withDescription("Devfile description")
            .withDevfile(
                newDto(DevfileDto.class)
                    .withApiVersion("1.0.0")
                    .withMetadata(newDto(MetadataDto.class).withGenerateName("gen-")))
      },
      {DtoConverter.asDto(TestObjectGenerator.createUserDevfile())}
    };
  }

  @DataProvider
  public Object[][] invalidUserDevfiles() {
    return new Object[][] {
      {
        newDto(UserDevfileDto.class)
            .withName("My devfile")
            .withDescription("Devfile description")
            .withDevfile(null),
        "Mandatory field `devfile` is not defined."
      },
      {
        newDto(UserDevfileDto.class)
            .withName("My devfile")
            .withDescription("Devfile description")
            .withDevfile(
                newDto(DevfileDto.class)
                    .withApiVersion(null)
                    .withMetadata(newDto(MetadataDto.class).withName("name"))),
        "Devfile schema validation failed. Error: Neither of `apiVersion` or `schemaVersion` found. This is not a valid devfile."
      }
    };
  }

  private static <T> T unwrapDto(Response response, Class<T> dtoClass) {
    return DtoFactory.getInstance().createDtoFromJson(response.asString(), dtoClass);
  }

  private static <T> List<T> unwrapDtoList(Response response, Class<T> dtoClass)
      throws IOException {
    return new ArrayList<>(
        DtoFactory.getInstance().createListDtoFromJson(response.body().asInputStream(), dtoClass));
  }

  @Filter
  public static class EnvironmentFilter implements RequestFilter {

    public void doFilter(GenericContainerRequest request) {
      EnvironmentContext.getCurrent().setSubject(TEST_SUBJECT);
    }
  }
}
