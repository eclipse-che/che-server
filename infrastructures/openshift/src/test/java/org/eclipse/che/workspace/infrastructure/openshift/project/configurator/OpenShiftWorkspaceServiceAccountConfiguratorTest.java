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
package org.eclipse.che.workspace.infrastructure.openshift.project.configurator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.*;

import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.openshift.OpenShiftClientFactory;
import org.eclipse.che.workspace.infrastructure.openshift.project.OpenShiftWorkspaceServiceAccount;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class OpenShiftWorkspaceServiceAccountConfiguratorTest {
  private final String SA_NAME = "test-serviceaccout";
  private final String CLUSTER_ROLES = "role1, role2";

  private final String WS_ID = "ws123";
  private final String USER_ID = "user123";
  private final String USERNAME = "user-che";

  private final String NS_NAME = "namespace-che";

  private NamespaceResolutionContext nsContext;

  @Mock private OpenShiftClientFactory clientFactory;

  private OpenShiftWorkspaceServiceAccountConfigurator saConfigurator;

  @BeforeMethod
  public void setUp() {
    nsContext = new NamespaceResolutionContext(WS_ID, USER_ID, USERNAME);
  }

  @Test
  public void testPreparesServiceAccount() throws InfrastructureException {
    saConfigurator =
        spy(
            new OpenShiftWorkspaceServiceAccountConfigurator(
                SA_NAME, CLUSTER_ROLES, clientFactory));
    OpenShiftWorkspaceServiceAccount serviceAccount = mock(OpenShiftWorkspaceServiceAccount.class);
    doReturn(serviceAccount).when(saConfigurator).createServiceAccount(WS_ID, NS_NAME);

    saConfigurator.configure(nsContext, NS_NAME);

    verify(serviceAccount).prepare();
  }

  @Test
  public void testDoNothingWhenServiceAccountNotSet() throws InfrastructureException {
    saConfigurator =
        spy(new OpenShiftWorkspaceServiceAccountConfigurator(null, CLUSTER_ROLES, clientFactory));

    saConfigurator.configure(nsContext, NS_NAME);

    verify(saConfigurator, times(0)).createServiceAccount(any(), any());
  }
}
