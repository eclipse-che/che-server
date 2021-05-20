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
package org.eclipse.che.api.workspace.server.spi.provision.env;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceImpl;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.WorkspaceDao;
import org.eclipse.che.commons.lang.Pair;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class WorkspaceNamespaceEnvVarProviderTest {

  @Mock WorkspaceDao workspaceDao;
  @Mock WorkspaceImpl workspace;
  @Mock RuntimeIdentity runtimeIdentity;
  WorkspaceNamespaceNameEnvVarProvider provider;

  @BeforeMethod
  public void setup() {
    provider = new WorkspaceNamespaceNameEnvVarProvider(workspaceDao);
  }

  @Test
  public void shouldReturnNamespaceVar()
      throws NotFoundException, ServerException, InfrastructureException {
    // given
    when(runtimeIdentity.getWorkspaceId()).thenReturn("ws-id111");
    doReturn(workspace).when(workspaceDao).get(Mockito.eq("ws-id111"));
    when(workspace.getNamespace()).thenReturn("ws-namespace");

    // when
    Pair<String, String> actual = provider.get(runtimeIdentity);

    // then
    assertEquals(actual.first, WorkspaceNamespaceNameEnvVarProvider.CHE_WORKSPACE_NAMESPACE);
    assertEquals(actual.second, "ws-namespace");
  }

  @Test(
      expectedExceptions = InfrastructureException.class,
      expectedExceptionsMessageRegExp =
          "Not able to get workspace namespace for workspace with id ws-id111")
  public void shouldWrapNotFoundExceptionException()
      throws NotFoundException, ServerException, InfrastructureException {
    // given
    when(runtimeIdentity.getWorkspaceId()).thenReturn("ws-id111");
    doThrow(new NotFoundException("Some message")).when(workspaceDao).get(Mockito.eq("ws-id111"));

    // when
    provider.get(runtimeIdentity);
  }

  @Test(
      expectedExceptions = InfrastructureException.class,
      expectedExceptionsMessageRegExp =
          "Not able to get workspace namespace for workspace with id ws-id111")
  public void shouldWrapServerExceptionException()
      throws NotFoundException, ServerException, InfrastructureException {
    // given
    when(runtimeIdentity.getWorkspaceId()).thenReturn("ws-id111");
    doThrow(new ServerException("Some message")).when(workspaceDao).get(Mockito.eq("ws-id111"));

    // when
    provider.get(runtimeIdentity);
  }
}
