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
package org.eclipse.che.inject;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Module;
import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.HandlesTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utility for finding Guice modules annotated with &#064DynaModule. */
@HandlesTypes({DynaModule.class})
public class ModuleScanner implements ServletContainerInitializer {
  private static final Logger LOG = LoggerFactory.getLogger(ModuleScanner.class);

  @VisibleForTesting static final List<Module> modules = new ArrayList<>();

  public static List<Module> findModules() {
    // also search if classes are provided through service loader mechanism
    // It's useful when the scanning is disabled or ServletContainerInitializer is disabled.
    // onStartup may not be called at all so it's another way of plugging modules.
    ServiceLoader<ModuleFinder> moduleFinderServiceLoader = ServiceLoader.load(ModuleFinder.class);
    moduleFinderServiceLoader.forEach(moduleFinder -> modules.addAll(moduleFinder.getModules()));
    return new ArrayList<>(modules);
  }

  @Override
  public void onStartup(Set<Class<?>> c, ServletContext ctx) throws ServletException {
    if (c != null) {
      for (Class<?> clazz : c) {
        if (Module.class.isAssignableFrom(clazz)) {
          try {
            modules.add((Module) clazz.newInstance());
          } catch (Exception e) {
            LOG.error("Problem with instantiating Module {} : {}", clazz, e.getMessage());
          }
        } else {
          LOG.warn(
              "Ignored non {} class annotated with {}",
              Module.class.getName(),
              DynaModule.class.getName());
        }
      }
    }
  }
}
