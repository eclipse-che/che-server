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
package org.eclipse.che.api.factory.server.jpa;

import com.google.inject.TypeLiteral;
import org.eclipse.che.api.factory.server.model.impl.FactoryImpl;
import org.eclipse.che.api.factory.server.spi.FactoryDao;
import org.eclipse.che.api.user.server.model.impl.UserImpl;
import org.eclipse.che.commons.test.tck.TckModule;
import org.eclipse.che.commons.test.tck.repository.JpaTckRepository;
import org.eclipse.che.commons.test.tck.repository.TckRepository;

/**
 * Tck module for factory test.
 *
 * @author Yevhenii Voevodin
 */
public class FactoryTckModule extends TckModule {

  @Override
  protected void configure() {
    bind(FactoryDao.class).to(JpaFactoryDao.class);

    bind(new TypeLiteral<TckRepository<FactoryImpl>>() {})
        .toInstance(new JpaTckRepository<>(FactoryImpl.class));
    bind(new TypeLiteral<TckRepository<UserImpl>>() {})
        .toInstance(new JpaTckRepository<>(UserImpl.class));
  }
}
