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
package org.eclipse.che.workspace.infrastructure.kubernetes.namespace.configurator;

import static java.util.stream.Collectors.joining;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesWorkspaceServiceAccount;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class WorkspaceServiceAccountConfiguratorTest {

  private WorkspaceServiceAccountConfigurator configurator;

  @Mock private CheServerKubernetesClientFactory clientFactory;
  private KubernetesClient client;
  private KubernetesServer serverMock;

  private NamespaceResolutionContext namespaceResolutionContext;
  @Mock private KubernetesWorkspaceServiceAccount kubeWSA;
  private final String TEST_NAMESPACE_NAME = "namespace123";
  private final String TEST_WORKSPACE_ID = "workspace123";
  private final String TEST_USER_ID = "user123";
  private final String TEST_USERNAME = "jondoe";
  private final String TEST_SERVICE_ACCOUNT = "serviceAccount123";
  private final String TEST_CLUSTER_ROLES = "cr1, cr2";

  @BeforeMethod
  public void setUp() throws InfrastructureException {
    configurator =
        spy(
            new WorkspaceServiceAccountConfigurator(
                TEST_SERVICE_ACCOUNT, TEST_CLUSTER_ROLES, clientFactory));
    //    when(configurator.doCreateServiceAccount(TEST_WORKSPACE_ID,
    // TEST_NAMESPACE_NAME)).thenReturn(kubeWSA);

    serverMock = new KubernetesServer(true, true);
    serverMock.before();
    client = spy(serverMock.getClient());
    lenient().when(clientFactory.create(TEST_WORKSPACE_ID)).thenReturn(client);

    namespaceResolutionContext =
        new NamespaceResolutionContext(TEST_WORKSPACE_ID, TEST_USER_ID, TEST_USERNAME);
  }

  @Test
  public void createWorkspaceServiceAccountWithBindings()
      throws InfrastructureException, InterruptedException {
    // given - cluster roles exists in cluster
    configurator =
        new WorkspaceServiceAccountConfigurator(
            TEST_SERVICE_ACCOUNT, TEST_CLUSTER_ROLES, clientFactory);
    client
        .rbac()
        .clusterRoles()
        .create(new ClusterRoleBuilder().withNewMetadata().withName("cr1").endMetadata().build());
    client
        .rbac()
        .clusterRoles()
        .create(new ClusterRoleBuilder().withNewMetadata().withName("cr2").endMetadata().build());

    // when
    configurator.configure(namespaceResolutionContext, TEST_NAMESPACE_NAME);

    // then - create service account with all the bindings
    var serviceAccount =
        client
            .serviceAccounts()
            .inNamespace(TEST_NAMESPACE_NAME)
            .withName(TEST_SERVICE_ACCOUNT)
            .get();
    assertNotNull(serviceAccount);

    var roleBindings =
        client.rbac().roleBindings().inNamespace(TEST_NAMESPACE_NAME).list().getItems();
    assertEquals(
        roleBindings.size(),
        6,
        roleBindings
            .stream()
            .map(r -> r.getMetadata().getName())
            .collect(joining(", "))); // exec, secrets, configmaps, view bindings + cr1, cr2
  }

  @Test
  public void dontCreateBindingsWhenClusterRolesDontExists() throws InfrastructureException {
    // given - cluster roles exists in cluster
    configurator =
        new WorkspaceServiceAccountConfigurator(
            TEST_SERVICE_ACCOUNT, TEST_CLUSTER_ROLES, clientFactory);

    // when
    configurator.configure(namespaceResolutionContext, TEST_NAMESPACE_NAME);

    // then - create service account with default bindings
    var serviceAccount =
        client
            .serviceAccounts()
            .inNamespace(TEST_NAMESPACE_NAME)
            .withName(TEST_SERVICE_ACCOUNT)
            .get();
    assertNotNull(serviceAccount);

    var roleBindings =
        client.rbac().roleBindings().inNamespace(TEST_NAMESPACE_NAME).list().getItems();
    assertEquals(
        roleBindings.size(),
        4,
        roleBindings
            .stream()
            .map(r -> r.getMetadata().getName())
            .collect(joining(", "))); // exec, secrets, configmaps, view bindings
  }
}
