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

import static java.util.stream.Collectors.toMap;

import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl;
import org.eclipse.che.api.factory.shared.dto.FactoryMetaDto;
import org.eclipse.che.api.workspace.shared.dto.devfile.DevfileDto;
import org.eclipse.che.api.workspace.shared.dto.devfile.ProjectDto;

/**
 * Defines a resolver that will produce factories for some parameters
 *
 * @author Florent Benoit
 */
public interface FactoryParametersResolver {
  /**
   * Resolver acceptance based on the given parameters.
   *
   * @param factoryParameters map of parameters dedicated to factories
   * @return true if it will be accepted by the resolver implementation or false if it is not
   *     accepted
   */
  boolean accept(@NotNull Map<String, String> factoryParameters);

  /**
   * Create factory object based on provided parameters
   *
   * @param factoryParameters map containing factory data parameters provided through URL
   * @throws BadRequestException when data are invalid
   */
  FactoryMetaDto createFactory(@NotNull Map<String, String> factoryParameters) throws ApiException;

  /**
   * Parses a factory Url String to a {@link RemoteFactoryUrl} object
   *
   * @param factoryUrl the factory Url string
   * @return {@link RemoteFactoryUrl} representation of the factory URL
   * @throws ApiException when authentication required operations fail
   */
  RemoteFactoryUrl parseFactoryUrl(String factoryUrl) throws ApiException;

  /**
   * Returns priority of the resolver. Resolvers with higher priority will be used among matched
   * resolvers.
   */
  default FactoryResolverPriority priority() {
    return FactoryResolverPriority.DEFAULT;
  }

  /**
   * Finds and returns devfile override parameters in general factory parameters map.
   *
   * @param factoryParameters map containing factory data parameters provided through URL
   * @return filtered devfile values override map
   */
  default Map<String, String> extractOverrideParams(Map<String, String> factoryParameters) {
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
  default void updateProjects(
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
