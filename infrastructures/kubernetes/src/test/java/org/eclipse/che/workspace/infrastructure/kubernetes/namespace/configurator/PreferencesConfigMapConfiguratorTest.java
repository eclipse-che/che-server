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

import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.AbstractWorkspaceServiceAccount.PREFERENCES_CONFIGMAP_NAME;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import java.util.Map;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class PreferencesConfigMapConfiguratorTest {
  private NamespaceConfigurator configurator;

  @Mock private KubernetesClientFactory clientFactory;
  private KubernetesServer serverMock;

  private NamespaceResolutionContext namespaceResolutionContext;
  private final String TEST_NAMESPACE_NAME = "namespace123";
  private final String TEST_WORKSPACE_ID = "workspace123";
  private final String TEST_USER_ID = "user123";
  private final String TEST_USERNAME = "jondoe";

  @BeforeMethod
  public void setUp() throws InfrastructureException {
    configurator = new PreferencesConfigMapConfigurator(clientFactory);

    serverMock = new KubernetesServer(true, true);
    serverMock.before();
    KubernetesClient client = spy(serverMock.getClient());
    when(clientFactory.create()).thenReturn(client);

    namespaceResolutionContext =
        new NamespaceResolutionContext(TEST_WORKSPACE_ID, TEST_USER_ID, TEST_USERNAME);
  }

  @Test
  public void createConfigmapWhenDoesntExist()
      throws InfrastructureException, InterruptedException {
    // given - clean env

    // when
    configurator.configure(namespaceResolutionContext, TEST_NAMESPACE_NAME);

    // then configmap created
    Assert.assertEquals(serverMock.getLastRequest().getMethod(), "POST");
    Assert.assertNotNull(
        serverMock
            .getClient()
            .configMaps()
            .inNamespace(TEST_NAMESPACE_NAME)
            .withName(PREFERENCES_CONFIGMAP_NAME)
            .get());
    verify(clientFactory, times(1)).create();
  }

  @Test
  public void doNothingWhenConfigmapExists() throws InfrastructureException, InterruptedException {
    // given - configmap already exists
    serverMock
        .getClient()
        .configMaps()
        .inNamespace(TEST_NAMESPACE_NAME)
        .create(
            new ConfigMapBuilder()
                .withNewMetadata()
                .withName(PREFERENCES_CONFIGMAP_NAME)
                .withAnnotations(Map.of("already", "created"))
                .endMetadata()
                .build());

    // when
    configurator.configure(namespaceResolutionContext, TEST_NAMESPACE_NAME);

    // then - don't create the configmap
    Assert.assertEquals(serverMock.getLastRequest().getMethod(), "GET");
    var configmaps =
        serverMock.getClient().configMaps().inNamespace(TEST_NAMESPACE_NAME).list().getItems();
    Assert.assertEquals(configmaps.size(), 1);
    Assert.assertEquals(configmaps.get(0).getMetadata().getAnnotations().get("already"), "created");
  }
}
