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

import java.util.Objects;
import org.flywaydb.core.internal.util.Location;
import org.flywaydb.core.internal.util.scanner.Resource;

/**
 * Data object for holding information about sql script.
 *
 * @author Yevhenii Voevodin
 */
class SqlScript {

  final Resource resource;
  final Location location;
  final String dir;
  final String vendor;
  final String name;

  SqlScript(Resource resource, Location location, String dir, String vendor, String name) {
    this.resource = resource;
    this.location = location;
    this.name = name;
    this.vendor = vendor;
    this.dir = dir;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof SqlScript)) {
      return false;
    }
    final SqlScript that = (SqlScript) obj;
    return Objects.equals(resource, that.resource)
        && Objects.equals(location, that.location)
        && Objects.equals(dir, that.dir)
        && Objects.equals(vendor, that.vendor)
        && Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 31 * hash + Objects.hashCode(resource);
    hash = 31 * hash + Objects.hashCode(location);
    hash = 31 * hash + Objects.hashCode(dir);
    hash = 31 * hash + Objects.hashCode(vendor);
    hash = 31 * hash + Objects.hashCode(name);
    return hash;
  }

  @Override
  public String toString() {
    return "SqlScript{"
        + "resource="
        + resource
        + ", location="
        + location
        + ", dir='"
        + dir
        + '\''
        + ", vendor='"
        + vendor
        + '\''
        + ", name='"
        + name
        + '\''
        + '}';
  }
}
