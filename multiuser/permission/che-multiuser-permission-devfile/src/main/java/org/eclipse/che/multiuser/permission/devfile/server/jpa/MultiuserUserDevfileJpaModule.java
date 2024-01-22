/*
 * Copyright (c) 2012-2024 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.multiuser.permission.devfile.server.jpa;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import org.eclipse.che.multiuser.api.permission.server.AbstractPermissionsDomain;
import org.eclipse.che.multiuser.permission.devfile.server.UserDevfileDomain;
import org.eclipse.che.multiuser.permission.devfile.server.model.impl.UserDevfilePermissionImpl;

public class MultiuserUserDevfileJpaModule extends AbstractModule {

  @Override
  protected void configure() {

    bind(new TypeLiteral<AbstractPermissionsDomain<UserDevfilePermissionImpl>>() {})
        .to(UserDevfileDomain.class);
  }
}
