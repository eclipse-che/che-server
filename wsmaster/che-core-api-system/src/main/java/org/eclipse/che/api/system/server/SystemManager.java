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
package org.eclipse.che.api.system.server;

import static org.eclipse.che.api.system.server.DtoConverter.asDto;
import static org.eclipse.che.api.system.shared.SystemStatus.PREPARING_TO_SHUTDOWN;
import static org.eclipse.che.api.system.shared.SystemStatus.READY_TO_SHUTDOWN;
import static org.eclipse.che.api.system.shared.SystemStatus.RUNNING;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.system.shared.SystemStatus;
import org.eclipse.che.api.system.shared.event.SystemStatusChangedEvent;
import org.eclipse.che.commons.lang.concurrent.LoggingUncaughtExceptionHandler;
import org.eclipse.che.commons.lang.concurrent.ThreadLocalPropagateContext;
import org.eclipse.che.core.db.DBTermination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Facade for system operations.
 *
 * @author Yevhenii Voevodin
 */
@Singleton
public class SystemManager {

  private static final Logger LOG = LoggerFactory.getLogger(SystemManager.class);

  private final AtomicReference<SystemStatus> statusRef;
  private final EventService eventService;
  private final ServiceTerminator terminator;
  private final DBTermination dbTermination;

  private final CountDownLatch shutdownLatch = new CountDownLatch(1);

  @Inject
  public SystemManager(
      ServiceTerminator terminator, DBTermination dbTermination, EventService eventService) {
    this.terminator = terminator;
    this.eventService = eventService;
    this.dbTermination = dbTermination;
    this.statusRef = new AtomicReference<>(RUNNING);
  }

  /**
   * Stops some of the system services preparing system to full shutdown. System status is changed
   * from {@link SystemStatus#RUNNING} to {@link SystemStatus#PREPARING_TO_SHUTDOWN}.
   *
   * @throws ConflictException when system status is different from running
   */
  public void stopServices() throws ConflictException {
    if (!statusRef.compareAndSet(RUNNING, PREPARING_TO_SHUTDOWN)) {
      throw new ConflictException(
          "System shutdown has been already called, system status: " + statusRef.get());
    }
    ExecutorService exec =
        Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder()
                .setDaemon(false)
                .setNameFormat("ShutdownSystemServicesPool")
                .setUncaughtExceptionHandler(LoggingUncaughtExceptionHandler.getInstance())
                .build());
    exec.execute(ThreadLocalPropagateContext.wrap(this::doStopServices));
    exec.shutdown();
  }

  /**
   * Suspends some of the system services preparing system to lighter shutdown. System status is
   * changed from {@link SystemStatus#RUNNING} to {@link SystemStatus#PREPARING_TO_SHUTDOWN}.
   *
   * @throws ConflictException when system status is different from running
   */
  public void suspendServices() throws ConflictException {
    if (!statusRef.compareAndSet(RUNNING, PREPARING_TO_SHUTDOWN)) {
      throw new ConflictException(
          "System shutdown has been already called, system status: " + statusRef.get());
    }
    ExecutorService exec =
        Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder()
                .setDaemon(false)
                .setNameFormat("SuspendSystemServicesPool")
                .setUncaughtExceptionHandler(LoggingUncaughtExceptionHandler.getInstance())
                .build());
    exec.execute(ThreadLocalPropagateContext.wrap(this::doSuspendServices));
    exec.shutdown();
  }

  /**
   * Gets current system status.
   *
   * @see SystemStatus
   */
  public SystemStatus getSystemStatus() {
    return statusRef.get();
  }

  /** Synchronously stops corresponding services. */
  private void doStopServices() {
    LOG.info("Preparing system to shutdown");
    eventService.publish(asDto(new SystemStatusChangedEvent(RUNNING, PREPARING_TO_SHUTDOWN)));
    try {
      terminator.terminateAll();
      statusRef.set(READY_TO_SHUTDOWN);
      eventService.publish(
          asDto(new SystemStatusChangedEvent(PREPARING_TO_SHUTDOWN, READY_TO_SHUTDOWN)));
      LOG.info("System is ready to shutdown");
    } catch (InterruptedException x) {
      LOG.error("Interrupted while waiting for system service to shutdown components");
      Thread.currentThread().interrupt();
    } finally {
      shutdownLatch.countDown();
    }
  }

  /** Synchronously stops corresponding services. */
  private void doSuspendServices() {
    LOG.info("Preparing system to shutdown");
    eventService.publish(asDto(new SystemStatusChangedEvent(RUNNING, PREPARING_TO_SHUTDOWN)));
    try {
      terminator.suspendAll();
      statusRef.set(READY_TO_SHUTDOWN);
      eventService.publish(
          asDto(new SystemStatusChangedEvent(PREPARING_TO_SHUTDOWN, READY_TO_SHUTDOWN)));
      LOG.info("System is ready to shutdown");
    } catch (InterruptedException x) {
      LOG.error("Interrupted while waiting for system service to shutdown components");
      Thread.currentThread().interrupt();
    } finally {
      shutdownLatch.countDown();
    }
  }

  @PreDestroy
  @VisibleForTesting
  void shutdown() throws InterruptedException {
    if (!statusRef.compareAndSet(RUNNING, PREPARING_TO_SHUTDOWN)) {
      shutdownLatch.await();
    } else {
      doSuspendServices();
      dbTermination.terminate();
    }
  }
}
