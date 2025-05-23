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
package org.eclipse.che.multiuser.keycloak.token.provider.deploy;

import com.google.inject.AbstractModule;
import org.eclipse.che.inject.DynaModule;

@DynaModule
public class KeycloakModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(org.eclipse.che.multiuser.keycloak.token.provider.contoller.TokenController.class);
  }
}
