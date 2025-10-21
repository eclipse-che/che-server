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
package org.eclipse.che.multiuser.permission.devfile.server.spi.jpa;

import com.google.inject.TypeLiteral;
import org.eclipse.che.account.spi.AccountImpl;
import org.eclipse.che.api.devfile.server.model.impl.UserDevfileImpl;
import org.eclipse.che.api.user.server.model.impl.UserImpl;
import org.eclipse.che.commons.test.tck.TckModule;
import org.eclipse.che.commons.test.tck.repository.JpaTckRepository;
import org.eclipse.che.commons.test.tck.repository.TckRepository;
import org.eclipse.che.multiuser.permission.devfile.server.model.UserDevfilePermission;

public class JpaTckModule extends TckModule {

  @Override
  protected void configure() {
    bind(new TypeLiteral<TckRepository<UserDevfilePermission>>() {})
        .toInstance(new JpaTckRepository<>(UserDevfilePermission.class));
    bind(new TypeLiteral<TckRepository<UserImpl>>() {})
        .toInstance(new JpaTckRepository<>(UserImpl.class));
    bind(new TypeLiteral<TckRepository<UserDevfileImpl>>() {})
        .toInstance(new JpaTckRepository<>(UserDevfileImpl.class));
    bind(new TypeLiteral<TckRepository<AccountImpl>>() {})
        .toInstance(new JpaTckRepository<>(AccountImpl.class));
  }
}
