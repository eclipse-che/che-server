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
package org.eclipse.che.commons.test;

import java.util.HashMap;
import java.util.Map;

/**
 * Example of usage in tests:
 *
 * <pre>{@code
 * SystemPropertiesHelper systemPropertiesHelper;
 *
 * public void setUp() {
 *     systemPropertiesHelper = SystemPropertiesHelper.overrideSystemProperties()
 *         .property("name1", "value1")
 *         .property("name2", "value2");
 * }
 *
 * public void tearDown() {
 *     systemPropertiesHelper.restoreFromBackup();
 * }
 * }</pre>
 */
public class SystemPropertiesHelper {

  private Map<String, String> backup;

  private SystemPropertiesHelper() {
    backup = new HashMap<>();
  }

  public static SystemPropertiesHelper overrideSystemProperties() {
    return new SystemPropertiesHelper();
  }

  public SystemPropertiesHelper property(String name, String value) {
    backupCurrentValue(name);
    System.setProperty(name, value);
    return this;
  }

  public SystemPropertiesHelper restoreFromBackup() {
    for (Map.Entry<String, String> entry : backup.entrySet()) {
      if (entry.getValue() == null) {
        System.clearProperty(entry.getKey());
      } else {
        System.setProperty(entry.getKey(), entry.getValue());
      }
    }
    backup.clear();
    return this;
  }

  private void backupCurrentValue(String name) {
    String value = System.getProperty(name);
    backup.put(name, value);
  }
}
