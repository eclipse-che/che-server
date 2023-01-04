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
package org.eclipse.che.api.factory.server.bitbucket;

import static org.eclipse.che.dto.server.DtoFactory.newDto;

import com.google.common.base.Strings;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;
import org.eclipse.che.api.workspace.shared.dto.ProjectConfigDto;
import org.eclipse.che.api.workspace.shared.dto.SourceStorageDto;
import org.eclipse.che.api.workspace.shared.dto.devfile.SourceDto;

/** Create {@link ProjectConfigDto} object from objects */
@Singleton
public class BitbucketSourceStorageBuilder {

  /**
   * Create SourceStorageDto DTO by using data of a bitbucket url
   *
   * @param bitbucketUrl an instance of {@link BitbucketUrl}
   * @return newly created source storage DTO object
   */
  public SourceStorageDto buildWorkspaceConfigSource(BitbucketUrl bitbucketUrl) {
    // Create map for source storage dto
    Map<String, String> parameters = new HashMap<>(1);
    if (!Strings.isNullOrEmpty(bitbucketUrl.getBranch())) {
      parameters.put("branch", bitbucketUrl.getBranch());
    }
    return newDto(SourceStorageDto.class)
        .withLocation(bitbucketUrl.repositoryLocation())
        .withType("bitbucket")
        .withParameters(parameters);
  }

  /**
   * Create SourceStorageDto DTO by using data of a bitbucket url
   *
   * @param bitbucketUrl an instance of {@link BitbucketUrl}
   * @return newly created source DTO object
   */
  public SourceDto buildDevfileSource(BitbucketUrl bitbucketUrl) {
    return newDto(SourceDto.class)
        .withLocation(bitbucketUrl.repositoryLocation())
        .withType("bitbucket")
        .withBranch(bitbucketUrl.getBranch());
  }
}
