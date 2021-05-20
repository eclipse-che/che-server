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
package org.eclipse.che.multiuser.organization.shared.dto;

import org.eclipse.che.api.core.notification.EventOrigin;
import org.eclipse.che.api.user.shared.dto.UserDto;
import org.eclipse.che.dto.shared.DTO;
import org.eclipse.che.multiuser.organization.shared.event.EventType;

/**
 * DTO for member added event.
 *
 * @author Anton Korneta
 */
@DTO
@EventOrigin("organization")
public interface MemberAddedEventDto extends OrganizationEventDto {

  @Override
  MemberAddedEventDto withOrganization(OrganizationDto organization);

  @Override
  MemberAddedEventDto withType(EventType eventType);

  UserDto getMember();

  void setMember(UserDto member);

  MemberAddedEventDto withMember(UserDto member);

  /** Returns name of user who initiated member invitation */
  String getInitiator();

  void setInitiator(String initiator);

  MemberAddedEventDto withInitiator(String initiator);
}
