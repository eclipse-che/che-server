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
package org.eclipse.che.api.factory.server;

import static java.util.stream.Collectors.toMap;
import static org.eclipse.che.api.factory.shared.Constants.CURRENT_VERSION;
import static org.eclipse.che.api.factory.shared.Constants.DEFAULT_DEVFILE;
import static org.eclipse.che.dto.server.DtoFactory.newDto;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.factory.server.scm.AuthorisationRequestManager;
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl;
import org.eclipse.che.api.factory.server.urlfactory.URLFactoryBuilder;
import org.eclipse.che.api.factory.shared.dto.FactoryDevfileV2Dto;
import org.eclipse.che.api.factory.shared.dto.FactoryMetaDto;
import org.eclipse.che.api.factory.shared.dto.FactoryVisitor;
import org.eclipse.che.api.workspace.server.devfile.FileContentProvider;
import org.eclipse.che.api.workspace.shared.dto.devfile.DevfileDto;
import org.eclipse.che.api.workspace.shared.dto.devfile.ProjectDto;

public class BaseFactoryParameterResolver {

  private final AuthorisationRequestManager authorisationRequestManager;
  private final URLFactoryBuilder urlFactoryBuilder;
  private final String providerName;

  public BaseFactoryParameterResolver(
      AuthorisationRequestManager authorisationRequestManager,
      URLFactoryBuilder urlFactoryBuilder,
      String providerName) {
    this.authorisationRequestManager = authorisationRequestManager;
    this.urlFactoryBuilder = urlFactoryBuilder;
    this.providerName = providerName;
  }

  protected FactoryMetaDto createFactory(
      Map<String, String> factoryParameters,
      RemoteFactoryUrl factoryUrl,
      FactoryVisitor factoryVisitor,
      FileContentProvider contentProvider)
      throws ApiException {

    // create factory from the following location if location exists, else create default factory
    return urlFactoryBuilder
        .createFactoryFromDevfile(
            factoryUrl,
            contentProvider,
            extractOverrideParams(factoryParameters),
            getSkipAuthorisation(factoryParameters))
        .orElseGet(
            () ->
                newDto(FactoryDevfileV2Dto.class)
                    .withDevfile(DEFAULT_DEVFILE)
                    .withV(CURRENT_VERSION)
                    .withSource("repo"))
        .acceptVisitor(factoryVisitor);
  }

  protected boolean getSkipAuthorisation(Map<String, String> factoryParameters) {
    String errorCode = "error_code";
    boolean stored = authorisationRequestManager.isStored(providerName);
    boolean skipAuthentication =
        factoryParameters.get(errorCode) != null
                && factoryParameters.get(errorCode).equals("access_denied")
            || stored;
    if (skipAuthentication && !stored) {
      authorisationRequestManager.store(providerName);
    }
    return skipAuthentication;
  }

  /**
   * Returns priority of the resolver. Resolvers with higher priority will be used among matched
   * resolvers.
   */
  public FactoryResolverPriority priority() {
    return FactoryResolverPriority.DEFAULT;
  }

  /**
   * Finds and returns devfile override parameters in general factory parameters map.
   *
   * @param factoryParameters map containing factory data parameters provided through URL
   * @return filtered devfile values override map
   */
  protected Map<String, String> extractOverrideParams(Map<String, String> factoryParameters) {
    String overridePrefix = "override.";
    return factoryParameters.entrySet().stream()
        .filter(e -> e.getKey().startsWith(overridePrefix))
        .collect(toMap(e -> e.getKey().substring(overridePrefix.length()), Map.Entry::getValue));
  }

  /**
   * If devfile has no projects, put there one provided by given `projectSupplier`. Otherwise update
   * all projects with given `projectModifier`.
   *
   * @param devfile of the projects to update
   * @param projectSupplier provides default project
   * @param projectModifier updates existing projects
   */
  protected void updateProjects(
      DevfileDto devfile,
      Supplier<ProjectDto> projectSupplier,
      Consumer<ProjectDto> projectModifier) {
    List<ProjectDto> projects = devfile.getProjects();
    if (projects.isEmpty()) {
      devfile.setProjects(Collections.singletonList(projectSupplier.get()));
    } else {
      // update existing project with same repository, set current branch if needed
      projects.forEach(projectModifier);
    }
  }
}
