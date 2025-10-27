/*
 * Copyright (c) 2012-2025 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.inject;

import static org.testng.Assert.assertEquals;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.name.Names;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.testng.annotations.Test;

/**
 * @author Tareq Sharafy
 */
public class PathConverterTest {

  @Test
  public void testConvertPaths() {
    Injector injector =
        Guice.createInjector(
            new PathConverter(),
            new Module() {
              @Override
              public void configure(Binder binder) {
                binder.bindConstant().annotatedWith(Names.named("abc")).to("aa/bb");
              }
            });
    Path path = injector.getInstance(Key.get(Path.class, Names.named("abc")));
    assertEquals(Paths.get("aa/bb"), path);
  }
}
