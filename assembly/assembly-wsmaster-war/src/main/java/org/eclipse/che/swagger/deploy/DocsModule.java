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
package org.eclipse.che.swagger.deploy;

import com.google.inject.AbstractModule;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;

/** @author Sergii Kabashniuk */
public class DocsModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(OpenApiResource.class);
  }
}
