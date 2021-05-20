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
package org.eclipse.che.api.workspace.server;

import org.eclipse.che.commons.lang.concurrent.Unlocker;

/**
 * Defines an abstract synchronization mechanism for operations associated with workspaces.(e.g.
 * sync statuses and runtime states). Note that different Che assemblies can provide various
 * implementations of the locking mechanism, these implementations are not required to follow the
 * contract defined by {@link java.util.concurrent.locks.ReadWriteLock}. For ease of use, acquired
 * locks are wrapped in {@link Unlocker}.
 *
 * @author Anton Korneta
 */
public interface WorkspaceLockService {

  /**
   * Acquires lock by given key. Returned instance may follow the read lock contract defined in
   * {@link java.util.concurrent.locks.ReadWriteLock}.
   *
   * @param key lock key
   * @return lock instance wrapped in {@link Unlocker}
   */
  Unlocker readLock(String key);

  /**
   * Acquires lock by given key. Returned instance may follow the write lock contract defined in
   * {@link java.util.concurrent.locks.ReadWriteLock}.
   *
   * @param key lock key
   * @return lock instance wrapped in {@link Unlocker}
   */
  Unlocker writeLock(String key);
}
