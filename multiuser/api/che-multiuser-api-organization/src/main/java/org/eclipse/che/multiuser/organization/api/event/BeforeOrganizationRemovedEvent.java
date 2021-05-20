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

import org.eclipse.che.core.db.cascade.event.RemoveEvent;
import org.eclipse.che.multiuser.organization.spi.impl.OrganizationImpl;

/**
 * Published before {@link OrganizationImpl organization} removed.
 *
 * @author Sergii Leschenko
 */
public class BeforeOrganizationRemovedEvent extends RemoveEvent {

  private final OrganizationImpl organization;

  public BeforeOrganizationRemovedEvent(OrganizationImpl organization) {
    this.organization = organization;
  }

  /** Returns organization which is going to be removed. */
  public OrganizationImpl getOrganization() {
    return organization;
  }
}
