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

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.Subject;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class UserPermissionConfiguratorTest {

  private NamespaceConfigurator configurator;

  @Mock private CheServerKubernetesClientFactory clientFactory;
  private KubernetesClient client;
  private KubernetesServer serverMock;

  private NamespaceResolutionContext namespaceResolutionContext;
  private final String TEST_NAMESPACE_NAME = "namespace123";
  private final String TEST_WORKSPACE_ID = "workspace123";
  private final String TEST_USER_ID = "user123";
  private final String TEST_USERNAME = "jondoe";
  private final String TEST_CLUSTER_ROLES = "cr1,cr2";

  @BeforeMethod
  public void setUp() throws InfrastructureException {
    configurator = new UserPermissionConfigurator(TEST_CLUSTER_ROLES, clientFactory);

    serverMock = new KubernetesServer(true, true);
    serverMock.before();
    client = spy(serverMock.getClient());
    lenient().when(clientFactory.create()).thenReturn(client);

    namespaceResolutionContext =
        new NamespaceResolutionContext(TEST_WORKSPACE_ID, TEST_USER_ID, TEST_USERNAME);
  }

  @Test
  public void doNothingWhenNoClusterRolesSet()
      throws InfrastructureException, InterruptedException {
    // given - no cluster roles set
    configurator = new UserPermissionConfigurator("", clientFactory);

    // when
    configurator.configure(namespaceResolutionContext, TEST_NAMESPACE_NAME);

    // then - do nothing
    Assert.assertNull(serverMock.getLastRequest());
    verify(clientFactory, never()).create();
  }

  @Test
  public void bindAllClusterRolesWhenEmptyEnv()
      throws InfrastructureException, InterruptedException {
    // given - clean env

    // when
    configurator.configure(namespaceResolutionContext, TEST_NAMESPACE_NAME);

    // then - create all role bindings
    var roleBindings =
        serverMock.getClient().rbac().roleBindings().inNamespace(TEST_NAMESPACE_NAME);
    Assert.assertEquals(roleBindings.list().getItems().size(), 2);

    var cr1 = roleBindings.withName("cr1").get();
    Assert.assertNotNull(cr1);
    Assert.assertEquals(cr1.getSubjects().get(0).getName(), TEST_USERNAME);
    Assert.assertEquals(cr1.getSubjects().get(0).getNamespace(), TEST_NAMESPACE_NAME);
    Assert.assertEquals(cr1.getRoleRef().getName(), "cr1");

    var cr2 = roleBindings.withName("cr2").get();
    Assert.assertNotNull(cr2);
    Assert.assertEquals(cr2.getSubjects().get(0).getName(), TEST_USERNAME);
    Assert.assertEquals(cr2.getSubjects().get(0).getNamespace(), TEST_NAMESPACE_NAME);
    Assert.assertEquals(cr2.getRoleRef().getName(), "cr2");

    verify(client, times(2)).rbac();
  }

  @Test
  public void replaceExistingBindingsWithSameName() throws InfrastructureException {
    // given - cr1 binding already exists
    client
        .rbac()
        .roleBindings()
        .inNamespace(TEST_NAMESPACE_NAME)
        .create(
            new RoleBindingBuilder()
                .withNewMetadata()
                .withName("cr1")
                .endMetadata()
                .withSubjects(new Subject("blabol", "blabol", "blabol", "blabol"))
                .withNewRoleRef()
                .withName("blabol")
                .endRoleRef()
                .build());

    // when
    configurator.configure(namespaceResolutionContext, TEST_NAMESPACE_NAME);

    // then
    var roleBindings = client.rbac().roleBindings().inNamespace(TEST_NAMESPACE_NAME);
    Assert.assertEquals(roleBindings.list().getItems().size(), 2);

    var cr1 = roleBindings.withName("cr1").get();
    Assert.assertEquals(cr1.getRoleRef().getName(), "cr1");
    Assert.assertEquals(cr1.getSubjects().size(), 1);
    Assert.assertEquals(cr1.getSubjects().get(0).getName(), TEST_USERNAME);
    Assert.assertEquals(cr1.getSubjects().get(0).getNamespace(), TEST_NAMESPACE_NAME);
  }

  @Test
  public void keepOtherClusterRoles() throws InfrastructureException {
    // given - some other binding in place
    client
        .rbac()
        .roleBindings()
        .inNamespace(TEST_NAMESPACE_NAME)
        .create(
            new RoleBindingBuilder()
                .withNewMetadata()
                .withName("othercr")
                .endMetadata()
                .withSubjects(new Subject("blabol", "blabol", "blabol", "blabol"))
                .withNewRoleRef()
                .withName("blabol")
                .endRoleRef()
                .build());

    // when
    configurator.configure(namespaceResolutionContext, TEST_NAMESPACE_NAME);

    // then
    var roleBindings = client.rbac().roleBindings().inNamespace(TEST_NAMESPACE_NAME);
    Assert.assertEquals(roleBindings.list().getItems().size(), 3);
  }
}
