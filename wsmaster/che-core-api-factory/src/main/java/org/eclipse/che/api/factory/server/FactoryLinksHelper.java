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

import static com.google.common.base.Strings.isNullOrEmpty;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_HTML;
import static org.eclipse.che.api.core.util.LinksHelper.createLink;
import static org.eclipse.che.api.factory.shared.Constants.FACTORY_ACCEPTANCE_REL_ATT;
import static org.eclipse.che.api.factory.shared.Constants.NAMED_FACTORY_ACCEPTANCE_REL_ATT;
import static org.eclipse.che.api.factory.shared.Constants.RETRIEVE_FACTORY_REL_ATT;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.UriBuilder;
import java.util.LinkedList;
import java.util.List;
import org.eclipse.che.api.core.rest.ServiceContext;
import org.eclipse.che.api.core.rest.shared.dto.Link;
import org.eclipse.che.api.factory.shared.dto.FactoryDevfileV2Dto;
import org.eclipse.che.api.factory.shared.dto.FactoryMetaDto;

/**
 * Helper class for creation links.
 *
 * @author Anton Korneta
 */
public class FactoryLinksHelper {

  private FactoryLinksHelper() {}

  /**
   * Creates factory links.
   *
   * @param serviceContext the context to retrieve factory service base URI
   * @return list of factory links
   */
  public static List<Link> createLinks(
      FactoryMetaDto factory,
      ServiceContext serviceContext,
      AdditionalFilenamesProvider additionalFilenamesProvider,
      String userName,
      String repositoryUrl) {
    final List<Link> links = new LinkedList<>();
    final UriBuilder uriBuilder = serviceContext.getServiceUriBuilder();
    final String factoryId = factory.getId();
    if (factoryId != null) {
      // creation of link to retrieve factory
      links.add(
          createLink(
              HttpMethod.GET,
              uriBuilder
                  .clone()
                  .path(FactoryService.class, "getFactory")
                  .build(factoryId)
                  .toString(),
              null,
              APPLICATION_JSON,
              RETRIEVE_FACTORY_REL_ATT));
      // creation of accept factory link
      final Link createWorkspace =
          createLink(
              HttpMethod.GET,
              uriBuilder.clone().replacePath("f").queryParam("id", factoryId).build().toString(),
              null,
              TEXT_HTML,
              FACTORY_ACCEPTANCE_REL_ATT);
      links.add(createWorkspace);
    }

    if (!isNullOrEmpty(factory.getName()) && !isNullOrEmpty(userName)) {
      // creation of accept factory link by name and creator
      final Link createWorkspaceFromNamedFactory =
          createLink(
              HttpMethod.GET,
              uriBuilder
                  .clone()
                  .replacePath("f")
                  .queryParam("name", factory.getName())
                  .queryParam("user", userName)
                  .build()
                  .toString(),
              null,
              TEXT_HTML,
              NAMED_FACTORY_ACCEPTANCE_REL_ATT);
      links.add(createWorkspaceFromNamedFactory);
    }

    if (factory instanceof FactoryDevfileV2Dto) {
      // link to devfile source
      if (!isNullOrEmpty(factory.getSource())) {
        links.add(
            createLink(
                HttpMethod.GET,
                uriBuilder
                    .clone()
                    .replacePath("api")
                    .path(ScmService.class)
                    .path(ScmService.class, "resolveFile")
                    .queryParam("repository", repositoryUrl)
                    .queryParam("file", factory.getSource())
                    .build(factoryId)
                    .toString(),
                factory.getSource() + " content"));
      }
      if (((FactoryDevfileV2Dto) factory).getScmInfo() != null) {
        // additional files links
        for (String additionalFile : additionalFilenamesProvider.get()) {
          links.add(
              createLink(
                  HttpMethod.GET,
                  uriBuilder
                      .clone()
                      .replacePath("api")
                      .path(ScmService.class)
                      .path(ScmService.class, "resolveFile")
                      .queryParam("repository", repositoryUrl)
                      .queryParam("file", additionalFile)
                      .build(factoryId)
                      .toString(),
                  additionalFile + " content"));
        }
      }
    }
    return links;
  }
}
