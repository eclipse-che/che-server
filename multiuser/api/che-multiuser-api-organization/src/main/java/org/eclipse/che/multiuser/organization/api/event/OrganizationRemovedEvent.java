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
package org.eclipse.che.multiuser.organization.api.event;

import static org.eclipse.che.multiuser.organization.shared.event.EventType.ORGANIZATION_REMOVED;

import java.util.List;
import org.eclipse.che.multiuser.organization.shared.event.EventType;
import org.eclipse.che.multiuser.organization.shared.event.OrganizationEvent;
import org.eclipse.che.multiuser.organization.shared.model.Organization;

/**
 * Defines organization removed event.
 *
 * @author Anton Korneta
 */
public class OrganizationRemovedEvent implements OrganizationEvent {

  private final String initiator;
  private final Organization organization;
  private final List<String> members;

  public OrganizationRemovedEvent(
      String initiator, Organization organization, List<String> members) {
    this.initiator = initiator;
    this.organization = organization;
    this.members = members;
  }

  @Override
  public EventType getType() {
    return ORGANIZATION_REMOVED;
  }

  @Override
  public Organization getOrganization() {
    return organization;
  }

  public List<String> getMembers() {
    return members;
  }

  /** Returns name of user who initiated organization removal */
  @Override
  public String getInitiator() {
    return initiator;
  }
}
