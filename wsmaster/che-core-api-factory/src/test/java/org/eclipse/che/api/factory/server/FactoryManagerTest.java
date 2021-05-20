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
package org.eclipse.che.api.factory.server;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertFalse;

import org.eclipse.che.api.factory.server.model.impl.FactoryImpl;
import org.eclipse.che.api.factory.server.spi.FactoryDao;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/** @author Max Shaposhnik (mshaposhnik@codenvy.com) on 3/20/17. */
@Listeners(value = {MockitoTestNGListener.class})
public class FactoryManagerTest {

  @Mock private FactoryDao factoryDao;

  @InjectMocks private FactoryManager factoryManager;

  @Captor private ArgumentCaptor<FactoryImpl> factoryCaptor;

  @Test
  public void shouldGenerateNameOnFactoryCreation() throws Exception {
    final FactoryImpl factory = FactoryImpl.builder().generateId().build();
    factoryManager.saveFactory(factory);
    verify(factoryDao).create(factoryCaptor.capture());
    assertFalse(isNullOrEmpty(factoryCaptor.getValue().getName()));
  }
}
