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

import java.net.URI;
import java.net.URISyntaxException;
import org.eclipse.che.api.core.model.workspace.runtime.Server;
import org.eclipse.che.api.workspace.server.hc.probe.HttpProbeConfig;
import org.eclipse.che.api.workspace.server.spi.InternalInfrastructureException;
import org.eclipse.che.commons.env.EnvironmentContext;

/**
 * Produces {@link HttpProbeConfig} for terminal agent liveness probes.
 *
 * @author Alexander Garagatyi
 */
public class TerminalServerLivenessProbeConfigFactory implements HttpProbeConfigFactory {
  private final int successThreshold;

  public TerminalServerLivenessProbeConfigFactory(int successThreshold) {
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
    URI uri;
    try {
      uri = new URI(server.getUrl());
    } catch (URISyntaxException e) {
      throw new InternalInfrastructureException(
          "Terminal agent server liveness probe url is invalid. Error: " + e.getMessage());
    }
    String protocol;
    if ("wss".equals(uri.getScheme())) {
      protocol = "https";
    } else {
      protocol = "http";
    }
    int port;
    if (uri.getPort() == -1) {
      if ("http".equals(protocol)) {
        port = 80;
      } else {
        port = 443;
      }
    } else {
      port = uri.getPort();
    }

    String path = uri.getPath().replaceFirst("/pty$", "/liveness");

    return new HttpProbeConfig(
        port, uri.getHost(), protocol, path, null, successThreshold, 3, 120, 10, 10);
  }
}
