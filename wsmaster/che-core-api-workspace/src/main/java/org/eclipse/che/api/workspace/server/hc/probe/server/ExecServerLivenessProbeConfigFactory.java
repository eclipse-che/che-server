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
package org.eclipse.che.api.workspace.server.hc.probe.server;

import java.net.MalformedURLException;
import java.net.URL;
import org.eclipse.che.api.core.model.workspace.runtime.Server;
import org.eclipse.che.api.workspace.server.hc.probe.HttpProbeConfig;
import org.eclipse.che.api.workspace.server.spi.InternalInfrastructureException;
import org.eclipse.che.commons.env.EnvironmentContext;

/**
 * Produces {@link HttpProbeConfig} for exec agent liveness probes.
 *
 * @author Alexander Garagatyi
 */
public class ExecServerLivenessProbeConfigFactory implements HttpProbeConfigFactory {
  private final int successThreshold;

  public ExecServerLivenessProbeConfigFactory(int successThreshold) {
    this.successThreshold = successThreshold;
  }

  @Override
  public HttpProbeConfig get(String workspaceId, Server server)
      throws InternalInfrastructureException {
    return get(EnvironmentContext.getCurrent().getSubject().getUserId(), workspaceId, server);
  }

  @Override
  public HttpProbeConfig get(String userId, String workspaceId, Server server)
      throws InternalInfrastructureException {
    try {
      URL url = new URL(server.getUrl());
      return new HttpProbeConfig(
          url.getPort() == -1 ? url.getDefaultPort() : url.getPort(),
          url.getHost(),
          url.getProtocol(),
          url.getPath().replaceFirst("/process$", "/liveness"),
          null,
          successThreshold,
          3,
          120,
          10,
          10);
    } catch (MalformedURLException e) {
      throw new InternalInfrastructureException(
          "Exec agent server liveness probe url is invalid. Error: " + e.getMessage());
    }
  }
}
