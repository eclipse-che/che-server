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

import java.util.Objects;
import org.eclipse.che.api.system.shared.event.SystemEvent;

/**
 * The base class for system service events.
 *
 * @author Yevhenii Voevodin
 */
public abstract class SystemServiceEvent implements SystemEvent {

  protected final String serviceName;

  protected SystemServiceEvent(String serviceName) {
    this.serviceName = Objects.requireNonNull(serviceName, "Service name required");
  }

  public String getServiceName() {
    return serviceName;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof SystemServiceEvent)) {
      return false;
    }
    final SystemServiceEvent that = (SystemServiceEvent) obj;
    return Objects.equals(getType(), that.getType())
        && Objects.equals(serviceName, that.serviceName);
  }

  @Override
  public int hashCode() {
    int hash = 17;
    hash = 31 * hash + Objects.hashCode(getType());
    hash = 31 * hash + Objects.hashCode(serviceName);
    return hash;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + "{eventType='"
        + getType()
        + "', serviceName="
        + serviceName
        + "'}";
  }
}
