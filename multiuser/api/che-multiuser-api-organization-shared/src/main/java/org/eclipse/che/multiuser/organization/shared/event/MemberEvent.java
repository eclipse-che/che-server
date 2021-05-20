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
package org.eclipse.che.multiuser.organization.shared.event;

import org.eclipse.che.api.core.model.user.User;

/**
 * Defines organization member event.
 *
 * @author Anton Korneta
 */
public interface MemberEvent extends OrganizationEvent {

  /** Returns the member associated with this event. */
  User getMember();
}
