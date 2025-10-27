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
package org.eclipse.che.api.workspace.shared.dto.event;

import org.eclipse.che.api.core.model.workspace.runtime.ServerStatus;
import org.eclipse.che.api.workspace.shared.dto.RuntimeIdentityDto;
import org.eclipse.che.dto.shared.DTO;

/**
 * @author gazarenkov
 */
@DTO
public interface ServerStatusEvent {

  ServerStatus getStatus();

  ServerStatusEvent withStatus(ServerStatus status);

  String getServerName();

  ServerStatusEvent withServerName(String serverName);

  String getServerUrl();

  ServerStatusEvent withServerUrl(String serverUrl);

  String getMachineName();

  ServerStatusEvent withMachineName(String machineName);

  /**
   * @return runtime identity
   */
  RuntimeIdentityDto getIdentity();

  ServerStatusEvent withIdentity(RuntimeIdentityDto identity);
}
