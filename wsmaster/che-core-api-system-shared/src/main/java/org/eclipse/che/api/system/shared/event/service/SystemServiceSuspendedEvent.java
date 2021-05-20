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
package org.eclipse.che.api.system.shared.event.service;

import org.eclipse.che.api.system.shared.event.EventType;

/**
 * See {@link EventType#SERVICE_SUSPENDED} description.
 *
 * @author Max Shaposhnyk
 */
public class SystemServiceSuspendedEvent extends SystemServiceEvent {

  public SystemServiceSuspendedEvent(String serviceName) {
    super(serviceName);
  }

  @Override
  public EventType getType() {
    return EventType.SERVICE_SUSPENDED;
  }
}
