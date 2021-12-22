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
package org.eclipse.che.api.workspace.server.hc;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.assistedinject.Assisted;
import jakarta.ws.rs.core.UriBuilder;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.core.model.workspace.runtime.Server;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.InternalInfrastructureException;
import org.eclipse.che.api.workspace.server.token.MachineTokenProvider;

/**
 * Checks readiness of servers of a machine.
 *
 * @author Alexander Garagatyi
 */
public class ServersChecker {
  private final RuntimeIdentity runtimeIdentity;
  private final String machineName;
  private final Map<String, ? extends Server> servers;
  private final MachineTokenProvider machineTokenProvider;
  private final int serverPingSuccessThreshold;
  private final long serverPingIntervalMillis;
  private final Set<String> livenessProbes;

  private Timer timer;
  private long resultTimeoutSeconds;
  private CompletableFuture<?> result;

  /**
   * Creates instance of this class.
   *
   * @param machineName name of machine whose servers will be checked by this method
   * @param servers map of servers in a machine
   */
  @Inject
  public ServersChecker(
      @Assisted RuntimeIdentity runtimeIdentity,
      @Assisted String machineName,
      @Assisted Map<String, ? extends Server> servers,
      MachineTokenProvider machineTokenProvider,
      @Named("che.workspace.server.ping_success_threshold") int serverPingSuccessThreshold,
      @Named("che.workspace.server.ping_interval_milliseconds") long serverPingInterval,
      @Named("che.workspace.server.liveness_probes") String[] livenessProbes) {
    this.runtimeIdentity = runtimeIdentity;
    this.machineName = machineName;
    this.servers = servers;
    this.timer = new Timer("ServersChecker", true);
    this.machineTokenProvider = machineTokenProvider;
    this.serverPingSuccessThreshold = serverPingSuccessThreshold;
    this.serverPingIntervalMillis = serverPingInterval;
    this.livenessProbes =
        Arrays.stream(livenessProbes).map(String::trim).collect(Collectors.toSet());
  }

  /**
   * Asynchronously starts checking readiness of servers of a machine. Method {@link #await()} waits
   * the result of this asynchronous check.
   *
   * @param serverReadinessHandler consumer which will be called with server reference as the
   *     argument when server become available
   * @throws InternalInfrastructureException if check of a server failed due to an unexpected error
   * @throws InfrastructureException if check of a server failed due to an error
   */
  public CompletableFuture<?> startAsync(Consumer<String> serverReadinessHandler)
      throws InfrastructureException {
    timer = new Timer("ServersChecker", true);
    List<ServerChecker> serverCheckers = getServerCheckers();
    // should be completed with an exception if a server considered unavailable
    CompletableFuture<Void> firstNonAvailable = new CompletableFuture<>();
    CompletableFuture[] checkTasks =
        serverCheckers.stream()
            .map(ServerChecker::getReportCompFuture)
            .map(
                compFut ->
                    compFut
                        .thenAccept(serverReadinessHandler)
                        .exceptionally(
                            e -> {
                              // cleanup checkers tasks
                              timer.cancel();
                              firstNonAvailable.completeExceptionally(e);
                              return null;
                            }))
            .toArray(CompletableFuture[]::new);
    resultTimeoutSeconds = checkTasks.length * 180;
    // should complete when all servers checks reported availability
    CompletableFuture<Void> allAvailable = CompletableFuture.allOf(checkTasks);
    // should complete when all servers are available or any server is unavailable
    result = CompletableFuture.anyOf(allAvailable, firstNonAvailable);
    for (ServerChecker serverChecker : serverCheckers) {
      serverChecker.start();
    }
    return result;
  }

  /**
   * Synchronously checks whether servers are available, throws {@link InfrastructureException} if
   * any is not.
   */
  public void checkOnce(Consumer<String> readyHandler) throws InfrastructureException {
    for (ServerChecker checker : getServerCheckers()) {
      checker.checkOnce(readyHandler);
    }
  }

  /**
   * Waits until servers are considered available or one of them is considered as unavailable.
   *
   * @throws InternalInfrastructureException if check of a server failed due to an unexpected error
   * @throws InfrastructureException if check of a server failed due to interruption
   * @throws InfrastructureException if check of a server failed because it reached timeout
   * @throws InfrastructureException if check of a server failed due to an error
   */
  public void await() throws InfrastructureException, InterruptedException {
    try {
      // TODO how much time should we check?
      result.get(resultTimeoutSeconds, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      throw new InfrastructureException(
          "Servers readiness check of machine " + machineName + " timed out");
    } catch (ExecutionException e) {
      try {
        throw e.getCause();
      } catch (InfrastructureException rethrow) {
        throw rethrow;
      } catch (Throwable thr) {
        throw new InternalInfrastructureException(
            "Machine "
                + machineName
                + " servers readiness check failed. Error: "
                + thr.getMessage(),
            thr);
      }
    }
  }

  private List<ServerChecker> getServerCheckers() throws InfrastructureException {
    ArrayList<ServerChecker> checkers = new ArrayList<>(servers.size());
    for (Map.Entry<String, ? extends Server> serverEntry : servers.entrySet()) {
      // TODO replace with correct behaviour
      // workaround needed because we don't have server readiness check in the model
      if (livenessProbes.contains(serverEntry.getKey())) {
        checkers.add(getChecker(serverEntry.getKey(), serverEntry.getValue()));
      }
    }
    return checkers;
  }

  private ServerChecker getChecker(String serverRef, Server server) throws InfrastructureException {
    // TODO replace with correct behaviour
    // workaround needed because we don't have server readiness check in the model
    // Create server readiness endpoint URL
    URL url;
    String token;
    try {
      String serverUrl = server.getUrl();

      if ("terminal".equals(serverRef)) {
        serverUrl = serverUrl.replaceFirst("^ws", "http").replaceFirst("/pty$", "/");
      }

      if ("wsagent/http".equals(serverRef) && !serverUrl.endsWith("/")) {
        // add trailing slash if it is not present
        serverUrl = serverUrl + '/';
      }

      token =
          machineTokenProvider.getToken(
              runtimeIdentity.getOwnerId(), runtimeIdentity.getWorkspaceId());
      url = UriBuilder.fromUri(serverUrl).build().toURL();
    } catch (MalformedURLException e) {
      throw new InternalInfrastructureException(
          "Server " + serverRef + " URL is invalid. Error: " + e.getMessage(), e);
    }

    return doCreateChecker(url, serverRef, token);
  }

  @VisibleForTesting
  ServerChecker doCreateChecker(URL url, String serverRef, String token) {
    // TODO add readiness endpoint to terminal and remove this
    // workaround needed because terminal server doesn't have endpoint to check it readiness
    if ("terminal".equals(serverRef)) {
      return new TerminalHttpConnectionServerChecker(
          url,
          machineName,
          serverRef,
          serverPingIntervalMillis,
          TimeUnit.SECONDS.toMillis(180),
          serverPingSuccessThreshold,
          TimeUnit.MILLISECONDS,
          timer,
          token);
    }
    // TODO do not hardcode timeouts, use server conf instead
    return new HttpConnectionServerChecker(
        url,
        machineName,
        serverRef,
        serverPingIntervalMillis,
        TimeUnit.SECONDS.toMillis(180),
        serverPingSuccessThreshold,
        TimeUnit.MILLISECONDS,
        timer,
        token);
  }
}
