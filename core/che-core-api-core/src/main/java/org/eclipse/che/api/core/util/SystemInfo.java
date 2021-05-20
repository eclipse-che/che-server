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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides information about operating system.
 *
 * @author <a href="mailto:aparfonov@codenvy.com">Andrey Parfonov</a>
 */
public class SystemInfo {
  private static final Logger LOG = LoggerFactory.getLogger(SystemInfo.class);

  public static final String OS = System.getProperty("os.name").toLowerCase();
  private static final boolean linux = OS.startsWith("linux");
  private static final boolean mac = OS.startsWith("mac");
  private static final boolean windows = OS.startsWith("windows");
  private static final boolean unix = !windows;

  public static boolean isLinux() {
    return linux;
  }

  public static boolean isWindows() {
    return windows;
  }

  public static boolean isMacOS() {
    return mac;
  }

  public static boolean isUnix() {
    return unix;
  }

  private SystemInfo() {}
}
