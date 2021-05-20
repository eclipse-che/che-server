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
package org.eclipse.che.api.workspace.shared.dto;

import java.util.Map;
import org.eclipse.che.api.core.model.workspace.runtime.Server;
import org.eclipse.che.api.core.model.workspace.runtime.ServerStatus;
import org.eclipse.che.dto.shared.DTO;

/**
 * Describes how to access to exposed ports for servers inside machine
 *
 * @author Alexander Garagatyi
 */
@DTO
public interface ServerDto extends Server {

  @Override
  String getUrl();

  void setUrl(String url);

  ServerDto withUrl(String url);

  @Override
  ServerStatus getStatus();

  ServerDto withStatus(ServerStatus status);

  @Override
  Map<String, String> getAttributes();

  ServerDto withAttributes(Map<String, String> attributes);
}
