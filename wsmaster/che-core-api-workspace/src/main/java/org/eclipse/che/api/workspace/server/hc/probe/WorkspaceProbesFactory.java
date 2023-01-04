/*
 * Copyright (c) 2012-2023 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.workspace.server.hc.probe;

import com.google.common.collect.ImmutableMap;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.inject.Inject;
import org.eclipse.che.api.core.model.workspace.Runtime;
import org.eclipse.che.api.core.model.workspace.runtime.Machine;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.core.model.workspace.runtime.Server;
import org.eclipse.che.api.workspace.server.hc.probe.server.ExecServerLivenessProbeConfigFactory;
import org.eclipse.che.api.workspace.server.hc.probe.server.HttpProbeConfigFactory;
import org.eclipse.che.api.workspace.server.hc.probe.server.TerminalServerLivenessProbeConfigFactory;
import org.eclipse.che.api.workspace.server.hc.probe.server.WsAgentServerLivenessProbeConfigFactory;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.InternalInfrastructureException;
import org.eclipse.che.api.workspace.server.token.MachineTokenProvider;
import org.eclipse.che.api.workspace.shared.Constants;

/**
 * Produces instances of {@link WorkspaceProbes} according to provided servers of a workspace
 * runtime.
 *
 * @author Alexander Garagatyi
 */
public class WorkspaceProbesFactory {
  // Is used to define servers which will be checked by this server checker class.
  // It is also a workaround to set correct paths for servers readiness checks.
  private final Map<String, HttpProbeConfigFactory> probeConfigFactories;

  @Inject
  public WorkspaceProbesFactory(MachineTokenProvider machineTokenProvider) {
    probeConfigFactories =
        ImmutableMap.of(
            Constants.SERVER_WS_AGENT_HTTP_REFERENCE,
            new WsAgentServerLivenessProbeConfigFactory(machineTokenProvider, 1),
            Constants.SERVER_EXEC_AGENT_HTTP_REFERENCE,
            new ExecServerLivenessProbeConfigFactory(1),
            Constants.SERVER_TERMINAL_REFERENCE,
            new TerminalServerLivenessProbeConfigFactory(1));
  }

  /**
   * Get {@link WorkspaceProbes} for a whole workspace runtime
   *
   * @throws InfrastructureException when the operation fails
   */
  public WorkspaceProbes getProbes(RuntimeIdentity runtimeId, Runtime runtime)
      throws InfrastructureException {
    return getProbes(runtimeId, runtime.getMachines());
  }

  /**
   * Get {@link WorkspaceProbes} for servers of the specified machines
   *
   * @throws InfrastructureException when the operation fails
   */
  public WorkspaceProbes getProbes(
      RuntimeIdentity runtimeId, Map<String, ? extends Machine> machines)
      throws InfrastructureException {
    List<ProbeFactory> factories = new ArrayList<>();
    try {
      for (Entry<String, ? extends Machine> entry : machines.entrySet()) {
        fillProbes(runtimeId, entry.getKey(), factories, entry.getValue().getServers());
      }
    } catch (MalformedURLException e) {
      throw new InternalInfrastructureException(
          "Server liveness probes creation failed. Error: " + e.getMessage());
    }

    return new WorkspaceProbes(runtimeId.getWorkspaceId(), factories);
  }

  /**
   * Get {@link WorkspaceProbes} for servers of a machine from a workspace runtime
   *
   * @throws InfrastructureException when the operation fails
   */
  public WorkspaceProbes getProbes(
      RuntimeIdentity runtimeId, String machineName, Map<String, ? extends Server> servers)
      throws InfrastructureException {
    List<ProbeFactory> factories = new ArrayList<>();
    try {
      fillProbes(runtimeId, machineName, factories, servers);
    } catch (MalformedURLException e) {
      throw new InternalInfrastructureException(
          "Server liveness probes creation failed. Error: " + e.getMessage());
    }
    return new WorkspaceProbes(runtimeId.getWorkspaceId(), factories);
  }

  private void fillProbes(
      RuntimeIdentity runtimeId,
      String machineName,
      List<ProbeFactory> factories,
      Map<String, ? extends Server> servers)
      throws InfrastructureException, MalformedURLException {
    for (Entry<String, ? extends Server> entry : servers.entrySet()) {
      ProbeFactory probeFactory =
          getProbeFactory(
              runtimeId.getOwnerId(),
              runtimeId.getWorkspaceId(),
              machineName,
              entry.getKey(),
              entry.getValue());
      if (probeFactory != null) {
        factories.add(probeFactory);
      }
    }
  }

  private ProbeFactory getProbeFactory(
      String userId, String workspaceId, String machineName, String serverRef, Server server)
      throws InfrastructureException, MalformedURLException {
    // workaround needed because we don't have server readiness check in the model
    HttpProbeConfigFactory configFactory = probeConfigFactories.get(serverRef);
    if (configFactory == null) {
      return null;
    }
    final HttpProbeConfig httpProbeConfig = configFactory.get(userId, workspaceId, server);
    return new HttpProbeFactory(workspaceId, machineName, serverRef, httpProbeConfig);
  }
}
