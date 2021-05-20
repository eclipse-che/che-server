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

import java.util.Set;
import org.eclipse.che.api.system.shared.event.service.StoppingSystemServiceEvent;
import org.eclipse.che.api.system.shared.event.service.SystemServiceItemStoppedEvent;
import org.eclipse.che.api.system.shared.event.service.SystemServiceStoppedEvent;

/**
 * Defines an interface for implementing termination or suspend process for a certain service.
 *
 * @author Yevhenii Voevodin
 */
public interface ServiceTermination {

  /**
   * Terminates a certain service. It's expected that termination is synchronous.
   *
   * @throws InterruptedException as termination is synchronous some of the implementations may need
   *     to wait for asynchronous jobs to finish their execution, so if termination is interrupted
   *     and implementation supports termination it should throw an interrupted exception
   */
  void terminate() throws InterruptedException;

  /**
   * Suspends a certain service. Means that no more new service entities should be created and/or
   * executed etc.
   *
   * @throws UnsupportedOperationException if this operation is not supported
   * @throws InterruptedException as suspend is synchronous some of the implementations may need to
   *     wait for asynchronous jobs to finish their execution, so if suspend is interrupted and
   *     implementation supports suspending it should throw an interrupted exception
   */
  default void suspend() throws InterruptedException, UnsupportedOperationException {
    throw new UnsupportedOperationException("This operation is not supported.");
  }

  /**
   * Returns the name of the service which is terminated by this termination. The name is used for
   * logging/sending events like {@link StoppingSystemServiceEvent}, {@link
   * SystemServiceItemStoppedEvent} or {@link SystemServiceStoppedEvent}.
   */
  String getServiceName();

  /**
   * Returns set of terminations service names on which the given termination depends, i.e. it MUST
   * be executed after them.
   *
   * @return list of dependencies is any, or empty list otherwise.
   */
  Set<String> getDependencies();
}
