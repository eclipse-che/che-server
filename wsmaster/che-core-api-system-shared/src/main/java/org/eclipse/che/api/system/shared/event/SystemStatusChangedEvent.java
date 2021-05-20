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
package org.eclipse.che.api.system.shared.event;

import java.util.Objects;
import org.eclipse.che.api.system.shared.SystemStatus;

/**
 * Describes system status changes.
 *
 * @author Yevhenii Voevodin
 */
public class SystemStatusChangedEvent implements SystemEvent {

  private final SystemStatus status;
  private final SystemStatus prevStatus;

  public SystemStatusChangedEvent(SystemStatus prevStatus, SystemStatus status) {
    this.status = status;
    this.prevStatus = prevStatus;
  }

  @Override
  public EventType getType() {
    return EventType.STATUS_CHANGED;
  }

  public SystemStatus getStatus() {
    return status;
  }

  public SystemStatus getPrevStatus() {
    return prevStatus;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof SystemStatusChangedEvent)) {
      return false;
    }
    final SystemStatusChangedEvent that = (SystemStatusChangedEvent) obj;
    return Objects.equals(status, that.status) && Objects.equals(prevStatus, that.prevStatus);
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 31 * hash + Objects.hashCode(status);
    hash = 31 * hash + Objects.hashCode(prevStatus);
    return hash;
  }

  @Override
  public String toString() {
    return "SystemStatusChangedEvent{" + "status=" + status + ", prevStatus=" + prevStatus + '}';
  }
}
