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
package org.eclipse.che.api.core.util;

/** @author andrew00x */
abstract class ProcessManager {
  static ProcessManager newInstance() {
    if (SystemInfo.isUnix()) {
      return new UnixProcessManager();
    }
    return new DefaultProcessManager();
  }

  abstract void kill(Process process);

  abstract boolean isAlive(Process process);

  abstract int system(String command);
}
