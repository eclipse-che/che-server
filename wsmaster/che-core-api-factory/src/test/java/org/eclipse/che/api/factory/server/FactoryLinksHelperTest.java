/*
 * Copyright (c) 2012-2021 Red Hat, Inc.
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

import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

import jakarta.ws.rs.core.UriBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.eclipse.che.api.core.rest.ServiceContext;
import org.eclipse.che.api.core.rest.shared.dto.Link;
import org.eclipse.che.api.factory.shared.dto.AuthorDto;
import org.eclipse.che.api.factory.shared.dto.FactoryDevfileV2Dto;
import org.eclipse.che.api.factory.shared.dto.ScmInfoDto;
import org.everrest.core.impl.uri.UriBuilderImpl;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class FactoryLinksHelperTest {

  private static final String USER_ID = "user123";
  private static final String URI_BASE = "http://localhost:8080";
  public static final String TEST_REPO = "https://test.repo.com";

  @Mock ServiceContext serviceContext;
  @Mock AdditionalFilenamesProvider additionalFilenamesProvider;

  @BeforeMethod
  public void setUp() {
    final UriBuilder uriBuilder = new UriBuilderImpl();
    uriBuilder.uri(URI_BASE);
    when(serviceContext.getServiceUriBuilder()).thenReturn(uriBuilder);
  }

  @Test
  public void shouldContainDevfileLinkIfSourceIsPresent() {
    final String testRepo = "https://test.repo.com";
    List<Link> links =
        FactoryLinksHelper.createLinks(
            createV2FactoryWithSource("factory1"),
            serviceContext,
            additionalFilenamesProvider,
            "user1",
            testRepo);
    assertTrue(
        links.stream()
            .anyMatch(
                l ->
                    l.getMethod().equals("GET")
                        && l.getRel().equals("devfile.yaml content")
                        && l.getHref()
                            .equals(
                                URI_BASE
                                    + "/api/scm/resolve?repository="
                                    + testRepo
                                    + "&file=devfile.yaml")));
  }

  @Test
  public void shouldContainFilesLinksIfScmInfoIsPresent() {
    when(additionalFilenamesProvider.get()).thenReturn(Collections.singletonList("myfile.ext"));
    List<Link> links =
        FactoryLinksHelper.createLinks(
            createV2FactoryWithScmInfo("factory1", TEST_REPO),
            serviceContext,
            additionalFilenamesProvider,
            "user1",
            TEST_REPO);
    assertTrue(
        links.stream()
            .anyMatch(
                l ->
                    l.getMethod().equals("GET")
                        && l.getRel().equals("myfile.ext content")
                        && l.getHref()
                            .equals(
                                URI_BASE
                                    + "/api/scm/resolve?repository="
                                    + TEST_REPO
                                    + "&file=myfile.ext")));
  }

  @Test
  public void shouldNotContainFilesLinksIfNoScmInfoIsPresent() {
    final String testRepo = "https://test.repo.com";
    List<Link> links =
        FactoryLinksHelper.createLinks(
            createV2FactoryWithSource("factory1"),
            serviceContext,
            additionalFilenamesProvider,
            "user1",
            testRepo);
    assertTrue(
        links.stream()
            .noneMatch(
                l -> l.getMethod().equals("GET") && l.getRel().equals("myfile.ext content")));
  }

  private FactoryDevfileV2Dto createV2FactoryWithScmInfo(String name, String testRepo) {
    return (FactoryDevfileV2Dto)
        newDto(FactoryDevfileV2Dto.class)
            .withV("4.0")
            .withDevfile(Map.of("a", "b"))
            .withScmInfo(
                newDto(ScmInfoDto.class)
                    .withRepositoryUrl(testRepo)
                    .withScmProviderName("prov1")
                    .withBranch("branch"))
            .withCreator(newDto(AuthorDto.class).withUserId(USER_ID).withCreated(12L))
            .withName(name);
  }

  private FactoryDevfileV2Dto createV2FactoryWithSource(String name) {
    return (FactoryDevfileV2Dto)
        newDto(FactoryDevfileV2Dto.class)
            .withV("4.0")
            .withDevfile(Map.of("a", "b"))
            .withSource("devfile.yaml")
            .withCreator(newDto(AuthorDto.class).withUserId(USER_ID).withCreated(12L))
            .withName(name);
  }
}
