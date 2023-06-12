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
package org.eclipse.che.api.factory.server.github;

import static org.eclipse.che.dto.server.DtoFactory.newDto;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;
import org.eclipse.che.api.workspace.shared.dto.ProjectConfigDto;
import org.eclipse.che.api.workspace.shared.dto.SourceStorageDto;
import org.eclipse.che.api.workspace.shared.dto.devfile.SourceDto;

/**
 * Create {@link ProjectConfigDto} object from objects
 *
 * @author Florent Benoit
 */
@Singleton
public class GithubSourceStorageBuilder {

  /**
   * Create SourceStorageDto DTO by using data of a github url
   *
   * @param githubUrl an instance of {@link GithubUrl}
   * @return newly created source storage DTO object
   */
  public SourceStorageDto buildWorkspaceConfigSource(GithubUrl githubUrl) {
    // Create map for source storage dto
    Map<String, String> parameters = new HashMap<>(2);
    parameters.put("branch", githubUrl.getBranch());

    return newDto(SourceStorageDto.class)
        .withLocation(githubUrl.repositoryLocation())
        .withType("github")
        .withParameters(parameters);
  }

  /**
   * Create SourceStorageDto DTO by using data of a github url
   *
   * @param githubUrl an instance of {@link GithubUrl}
   * @return newly created source DTO object
   */
  public SourceDto buildDevfileSource(GithubUrl githubUrl) {
    return newDto(SourceDto.class)
        .withLocation(githubUrl.repositoryLocation())
        .withType("github")
        .withBranch(githubUrl.getBranch());
  }
}
