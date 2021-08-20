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
package org.eclipse.che.api.user.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import org.eclipse.che.api.core.model.user.Profile;
import org.eclipse.che.api.core.rest.shared.dto.Link;
import org.eclipse.che.dto.server.DtoFactory;
import org.eclipse.che.dto.shared.DTO;

/**
 * This object used for transporting profile data to/from client.
 *
 * @author Yevhenii Voevodin
 * @see Profile
 * @see DtoFactory
 */
@DTO
public interface ProfileDto extends Profile {

  void setUserId(String id);

  @Schema(description = "Profile ID")
  String getUserId();

  ProfileDto withUserId(String id);

  @Schema(description = "Profile attributes")
  Map<String, String> getAttributes();

  void setAttributes(Map<String, String> attributes);

  ProfileDto withAttributes(Map<String, String> attributes);

  List<Link> getLinks();

  void setLinks(List<Link> links);

  ProfileDto withLinks(List<Link> links);

  String getEmail();

  ProfileDto withEmail(String email);
}
