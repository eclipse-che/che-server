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

import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.AbstractWorkspaceServiceAccount.CREDENTIALS_SECRET_NAME;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.api.model.SecretBuilder;
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
public class CredentialsSecretConfiguratorTest {

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
    configurator = new CredentialsSecretConfigurator(clientFactory);

    serverMock = new KubernetesServer(true, true);
    serverMock.before();
    KubernetesClient client = spy(serverMock.getClient());
    when(clientFactory.create()).thenReturn(client);

    namespaceResolutionContext =
        new NamespaceResolutionContext(TEST_WORKSPACE_ID, TEST_USER_ID, TEST_USERNAME);
  }

  @Test
  public void createCredentialsSecretWhenDoesNotExist()
      throws InfrastructureException, InterruptedException {
    // given - clean env

    // when
    configurator.configure(namespaceResolutionContext, TEST_NAMESPACE_NAME);

    // then create a secret
    Assert.assertEquals(serverMock.getLastRequest().getMethod(), "POST");
    Assert.assertNotNull(
        serverMock
            .getClient()
            .secrets()
            .inNamespace(TEST_NAMESPACE_NAME)
            .withName(CREDENTIALS_SECRET_NAME)
            .get());
  }

  @Test
  public void doNothingWhenSecretAlreadyExists()
      throws InfrastructureException, InterruptedException {
    // given - secret already exists
    serverMock
        .getClient()
        .secrets()
        .inNamespace(TEST_NAMESPACE_NAME)
        .create(
            new SecretBuilder()
                .withNewMetadata()
                .withName(CREDENTIALS_SECRET_NAME)
                .withAnnotations(Map.of("already", "created"))
                .endMetadata()
                .build());

    // when
    configurator.configure(namespaceResolutionContext, TEST_NAMESPACE_NAME);

    // then - don't create the secret
    Assert.assertEquals(serverMock.getLastRequest().getMethod(), "GET");
    var secrets =
        serverMock.getClient().secrets().inNamespace(TEST_NAMESPACE_NAME).list().getItems();
    Assert.assertEquals(secrets.size(), 1);
    Assert.assertEquals(secrets.get(0).getMetadata().getAnnotations().get("already"), "created");
  }
}
