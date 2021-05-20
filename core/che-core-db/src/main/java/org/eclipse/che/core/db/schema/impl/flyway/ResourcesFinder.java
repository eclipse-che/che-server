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
package org.eclipse.che.core.db.schema.impl.flyway;

import static com.google.common.collect.Lists.newArrayList;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.api.configuration.FlywayConfiguration;
import org.flywaydb.core.internal.util.Location;
import org.flywaydb.core.internal.util.scanner.Resource;
import org.flywaydb.core.internal.util.scanner.classpath.ClassPathScanner;
import org.flywaydb.core.internal.util.scanner.filesystem.FileSystemScanner;

/**
 * Searches for sql scripts in given places.
 *
 * @author Yevhenii Voevodin
 */
class ResourcesFinder {

  /**
   * Finds script resources in configured {@link FlywayConfiguration#getLocations()}.
   *
   * @param configuration flyway configuration to find scripts
   * @return found scripts or an empty list if nothing found
   * @throws IOException when any io error occurs during scripts look up
   */
  Map<Location, List<Resource>> findResources(FlywayConfiguration configuration)
      throws IOException {
    final String prefix = configuration.getSqlMigrationPrefix();
    final String suffix = configuration.getSqlMigrationSuffix();
    final ClassPathScanner cpScanner = new ClassPathScanner(configuration.getClassLoader());
    final FileSystemScanner fsScanner = new FileSystemScanner();
    final Map<Location, List<Resource>> resources = new HashMap<>();
    for (String rawLocation : configuration.getLocations()) {
      final Location location = new Location(rawLocation);
      if (location.isClassPath()) {
        resources.put(location, newArrayList(cpScanner.scanForResources(location, prefix, suffix)));
      } else {
        resources.put(location, newArrayList(fsScanner.scanForResources(location, prefix, suffix)));
      }
    }
    return resources;
  }
}
