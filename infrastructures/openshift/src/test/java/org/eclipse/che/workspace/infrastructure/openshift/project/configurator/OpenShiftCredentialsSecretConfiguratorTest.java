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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.server.mock.OpenShiftServer;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.openshift.OpenShiftClientFactory;
import org.eclipse.che.workspace.infrastructure.openshift.project.configurator.OpenShiftCredentialsSecretConfigurator;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class OpenShiftCredentialsSecretConfiguratorTest {

  private NamespaceConfigurator configurator;

  @Mock private OpenShiftClientFactory clientFactory;
  private OpenShiftServer serverMock;

  private NamespaceResolutionContext namespaceResolutionContext;
  private final String TEST_NAMESPACE_NAME = "namespace123";
  private final String TEST_WORKSPACE_ID = "workspace123";
  private final String TEST_USER_ID = "user123";
  private final String TEST_USERNAME = "jondoe";

  @BeforeMethod
  public void setUp() throws InfrastructureException {
    configurator = new OpenShiftCredentialsSecretConfigurator(clientFactory);

    serverMock = new OpenShiftServer(true, true);
    serverMock.before();
    OpenShiftClient client = spy(serverMock.getOpenshiftClient());
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
            .getOpenshiftClient()
            .secrets()
            .inNamespace(TEST_NAMESPACE_NAME)
            .withName(CREDENTIALS_SECRET_NAME)
            .get());
    verify(clientFactory, times(2)).create();
  }

  @Test
  public void doNothingWhenSecretAlreadyExists()
      throws InfrastructureException, InterruptedException {
    // given - secret already exists
    serverMock
        .getOpenshiftClient()
        .secrets()
        .inNamespace(TEST_NAMESPACE_NAME)
        .create(
            new SecretBuilder()
                .withNewMetadata()
                .withName(CREDENTIALS_SECRET_NAME)
                .endMetadata()
                .build());

    // when
    configurator.configure(namespaceResolutionContext, TEST_NAMESPACE_NAME);

    // then - don't create the secret
    Assert.assertEquals(serverMock.getLastRequest().getMethod(), "GET");
    verify(clientFactory, times(1)).create();
  }
}
