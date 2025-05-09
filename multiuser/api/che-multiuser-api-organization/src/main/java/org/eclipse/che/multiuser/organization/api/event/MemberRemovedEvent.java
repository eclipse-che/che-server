/*
 * Copyright (c) 2012-2025 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.multiuser.organization.api.event;

import org.eclipse.che.api.core.model.user.User;
import org.eclipse.che.multiuser.organization.shared.event.EventType;
import org.eclipse.che.multiuser.organization.shared.event.MemberEvent;
import org.eclipse.che.multiuser.organization.shared.model.Organization;

/**
 * Defines the event for organization member removal.
 *
 * @author Anton Korneta
 */
public class MemberRemovedEvent implements MemberEvent {

  private final String initiator;
  private final User member;
  private final Organization organization;

  public MemberRemovedEvent(String initiator, User member, Organization organization) {
    this.initiator = initiator;
    this.member = member;
    this.organization = organization;
  }

  @Override
  public EventType getType() {
    return EventType.MEMBER_REMOVED;
  }

  @Override
  public Organization getOrganization() {
    return organization;
  }

  @Override
  public User getMember() {
    return member;
  }

  @Override
  public String getInitiator() {
    return initiator;
  }
}
