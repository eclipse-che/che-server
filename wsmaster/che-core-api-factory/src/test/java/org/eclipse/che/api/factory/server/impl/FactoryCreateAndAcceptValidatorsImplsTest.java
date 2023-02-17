/*
 * Copyright (c) 2012-2023 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.factory.server.impl;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.factory.shared.dto.FactoryDto;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Tests for {@link org.eclipse.che.api.factory.server.impl.FactoryAcceptValidatorImpl} and {@link
 * FactoryCreateValidatorImpl}
 */
@Listeners(value = {MockitoTestNGListener.class})
public class FactoryCreateAndAcceptValidatorsImplsTest {

  @Mock private FactoryDto factory;

  private FactoryAcceptValidatorImpl acceptValidator;

  private FactoryCreateValidatorImpl createValidator;

  @BeforeMethod
  public void setUp() throws Exception {

    acceptValidator = new FactoryAcceptValidatorImpl();
    createValidator = new FactoryCreateValidatorImpl();
  }

  @Test
  public void testValidateOnCreate() throws Exception {
    FactoryCreateValidatorImpl spy = spy(createValidator);
    doNothing().when(spy).validateProjects(any(FactoryDto.class));
    doNothing().when(spy).validateCurrentTimeAfterSinceUntil(any(FactoryDto.class));
    doNothing().when(spy).validateProjectActions(any(FactoryDto.class));

    // main invoke
    spy.validateOnCreate(factory);

    verify(spy).validateProjects(any(FactoryDto.class));
    verify(spy).validateCurrentTimeAfterSinceUntil(any(FactoryDto.class));
    verify(spy).validateOnCreate(any(FactoryDto.class));
    verify(spy).validateProjectActions(any(FactoryDto.class));
    verifyNoMoreInteractions(spy);
  }

  @Test
  public void testOnAcceptEncoded() throws ApiException {
    FactoryAcceptValidatorImpl spy = spy(acceptValidator);
    doNothing().when(spy).validateCurrentTimeBetweenSinceUntil(any(FactoryDto.class));
    doNothing().when(spy).validateProjectActions(any(FactoryDto.class));

    // main invoke
    spy.validateOnAccept(factory);

    verify(spy).validateCurrentTimeBetweenSinceUntil(any(FactoryDto.class));
    verify(spy).validateOnAccept(any(FactoryDto.class));
    verify(spy).validateProjectActions(any(FactoryDto.class));
    verifyNoMoreInteractions(spy);
  }
}
