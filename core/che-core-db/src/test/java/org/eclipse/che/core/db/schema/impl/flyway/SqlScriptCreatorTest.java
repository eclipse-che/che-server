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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.internal.util.Location;
import org.flywaydb.core.internal.util.scanner.Resource;
import org.flywaydb.core.internal.util.scanner.filesystem.FileSystemResource;
import org.testng.annotations.Test;

/**
 * Tests {@link SqlScriptCreator}.
 *
 * @author Yevhenii Voevodin
 */
public class SqlScriptCreatorTest {

  @Test
  public void createsScript() throws Exception {
    final Location location = new Location("filesystem:schema");
    final Resource resource = new FileSystemResource("schema/5.0.0-M7/v1__init.sql");

    final SqlScriptCreator scriptsCreator = new SqlScriptCreator();
    final SqlScript script = scriptsCreator.createScript(location, resource);

    assertEquals(script.name, "v1__init.sql");
    assertEquals(script.location, location);
    assertEquals(script.dir, "5.0.0-M7");
    assertEquals(script.resource.getLocation(), resource.getLocation());
    assertNull(script.vendor);
  }

  @Test
  public void createsVendorScript() throws Exception {
    final Location location = new Location("filesystem:schema");
    final Resource resource = new FileSystemResource("schema/5.0.0-M7/postgresql/v1__init.sql");

    final SqlScriptCreator scriptsCreator = new SqlScriptCreator();
    final SqlScript script = scriptsCreator.createScript(location, resource);

    assertEquals(script.name, "v1__init.sql");
    assertEquals(script.location, location);
    assertEquals(script.dir, "5.0.0-M7");
    assertEquals(script.resource.getLocation(), resource.getLocation());
    assertEquals(script.vendor, "postgresql");
  }

  @Test(expectedExceptions = FlywayException.class)
  public void failsToCreateResourceWhenPathIsInvalid() throws Exception {
    final Location location = new Location("filesystem:schema");
    final Resource resource = new FileSystemResource("schema/v1__init.sql");

    new SqlScriptCreator().createScript(location, resource);
  }
}
