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
package org.eclipse.che.multiuser.organization.api.listener;

import static org.eclipse.che.multiuser.organization.api.DtoConverter.asDto;

import jakarta.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.user.User;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.core.notification.EventSubscriber;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.multiuser.api.permission.shared.event.PermissionsEvent;
import org.eclipse.che.multiuser.api.permission.shared.model.Permissions;
import org.eclipse.che.multiuser.organization.api.OrganizationManager;
import org.eclipse.che.multiuser.organization.api.event.MemberAddedEvent;
import org.eclipse.che.multiuser.organization.api.event.MemberRemovedEvent;
import org.eclipse.che.multiuser.organization.api.permissions.OrganizationDomain;
import org.eclipse.che.multiuser.organization.shared.model.Organization;

/**
 * Maps permissions to organization related events.
 *
 * @author Anton Korneta
 */
@Singleton
public class MemberEventsPublisher implements EventSubscriber<PermissionsEvent> {

  private final EventService eventService;
  private final UserManager userManager;
  private final OrganizationManager organizationManager;

  @Inject
  public MemberEventsPublisher(
      EventService eventService, UserManager userManager, OrganizationManager organizationManager) {
    this.eventService = eventService;
    this.userManager = userManager;
    this.organizationManager = organizationManager;
  }

  @PostConstruct
  private void subscribe() {
    eventService.subscribe(this);
  }

  @Override
  public void onEvent(PermissionsEvent event) {
    final Permissions permissions = event.getPermissions();
    if (OrganizationDomain.DOMAIN_ID.equals(permissions.getDomainId())) {
      try {
        switch (event.getType()) {
          case PERMISSIONS_ADDED:
            {
              final String initiator = event.getInitiator();
              final User addedMember = userManager.getById(permissions.getUserId());
              final Organization org = organizationManager.getById(permissions.getInstanceId());
              eventService.publish(asDto(new MemberAddedEvent(initiator, addedMember, org)));
              break;
            }
          case PERMISSIONS_REMOVED:
            {
              final String initiator = event.getInitiator();
              final User removedMember = userManager.getById(permissions.getUserId());
              final Organization org = organizationManager.getById(permissions.getInstanceId());
              eventService.publish(asDto(new MemberRemovedEvent(initiator, removedMember, org)));
              break;
            }
          default:
            // do nothing
        }
      } catch (NotFoundException | ServerException ignored) {
      }
    }
  }
}
