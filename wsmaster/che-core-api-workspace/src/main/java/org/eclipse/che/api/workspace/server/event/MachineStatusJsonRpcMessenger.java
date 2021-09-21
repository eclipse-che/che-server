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
package org.eclipse.che.api.workspace.server.event;

import static org.eclipse.che.api.workspace.shared.Constants.MACHINE_STATUS_CHANGED_METHOD;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.core.notification.RemoteSubscriptionManager;
import org.eclipse.che.api.workspace.shared.dto.event.MachineStatusEvent;

/** Send workspace events using JSON RPC to the clients */
@Singleton
public class MachineStatusJsonRpcMessenger {
  private final RemoteSubscriptionManager remoteSubscriptionManager;

  @Inject
  public MachineStatusJsonRpcMessenger(RemoteSubscriptionManager remoteSubscriptionManager) {
    this.remoteSubscriptionManager = remoteSubscriptionManager;
  }

  @PostConstruct
  private void postConstruct() {
    remoteSubscriptionManager.register(
        MACHINE_STATUS_CHANGED_METHOD, MachineStatusEvent.class, this::predicate);
  }

  private boolean predicate(MachineStatusEvent event, Map<String, String> scope) {
    return event.getIdentity().getWorkspaceId().equals(scope.get("workspaceId"));
  }
}
