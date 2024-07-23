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
package org.eclipse.che.api.factory.server;

import static java.util.Collections.emptyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Map;
import org.eclipse.che.api.factory.server.scm.AuthorisationRequestManager;
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl;
import org.eclipse.che.api.factory.server.urlfactory.URLFactoryBuilder;
import org.eclipse.che.api.factory.shared.dto.FactoryDevfileV2Dto;
import org.eclipse.che.api.factory.shared.dto.FactoryVisitor;
import org.eclipse.che.api.workspace.server.devfile.FileContentProvider;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(value = {MockitoTestNGListener.class})
public class BaseFactoryParameterResolverTest {

  @Mock private AuthorisationRequestManager authorisationRequestManager;
  @Mock private URLFactoryBuilder urlFactoryBuilder;
  @Mock private RemoteFactoryUrl remoteFactoryUrl;
  @Mock private FactoryVisitor factoryVisitor;
  @Mock private FileContentProvider contentProvider;
  private static final String PROVIDER_NAME = "test";

  private BaseFactoryParameterResolver baseFactoryParameterResolver;

  @BeforeMethod
  protected void init() throws Exception {
    baseFactoryParameterResolver =
        new BaseFactoryParameterResolver(
            authorisationRequestManager, urlFactoryBuilder, PROVIDER_NAME);
  }

  @Test
  public void shouldReturnFalseOnGetSkipAuthorisation() {
    // given
    when(authorisationRequestManager.isStored(eq(PROVIDER_NAME))).thenReturn(false);
    // when
    boolean result = baseFactoryParameterResolver.getSkipAuthorisation(emptyMap());
    // then
    assertFalse(result);
  }

  @Test
  public void shouldReturnTrueOnGetSkipAuthorisation() {
    // given
    when(authorisationRequestManager.isStored(eq(PROVIDER_NAME))).thenReturn(true);
    // when
    boolean result = baseFactoryParameterResolver.getSkipAuthorisation(emptyMap());
    // then
    assertTrue(result);
  }

  @Test
  public void shouldReturnTrueOnGetSkipAuthorisationFromFactoryParams() {
    // given
    when(authorisationRequestManager.isStored(eq(PROVIDER_NAME))).thenReturn(false);
    // when
    boolean result =
        baseFactoryParameterResolver.getSkipAuthorisation(Map.of("error_code", "access_denied"));
    // then
    assertTrue(result);
  }

  @Test
  public void shouldReturnDevfileV2() throws Exception {
    // given
    when(authorisationRequestManager.isStored(eq(PROVIDER_NAME))).thenReturn(false);
    // when
    baseFactoryParameterResolver.createFactory(
        emptyMap(), remoteFactoryUrl, factoryVisitor, contentProvider);
    // then
    verify(factoryVisitor).visit(any(FactoryDevfileV2Dto.class));
  }
}
