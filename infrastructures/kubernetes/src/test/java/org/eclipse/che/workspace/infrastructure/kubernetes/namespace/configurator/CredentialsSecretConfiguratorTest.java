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
package org.eclipse.che.workspace.infrastructure.kubernetes.namespace.configurator;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import java.util.Base64;
import java.util.Map;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class CredentialsSecretConfiguratorTest {

  private NamespaceConfigurator configurator;

  @Mock private CheServerKubernetesClientFactory cheServerKubernetesClientFactory;
  @Mock private PersonalAccessTokenManager personalAccessTokenManager;
  private KubernetesServer serverMock;

  private NamespaceResolutionContext namespaceResolutionContext;
  private final String TEST_NAMESPACE_NAME = "namespace123";
  private final String TEST_WORKSPACE_ID = "workspace123";
  private final String TEST_USER_ID = "user123";
  private final String TEST_USERNAME = "jondoe";
  private final String PAT_SECRET_NAME = "personal-access-token-1";
  private static final String MERGED_GIT_CREDENTIALS_SECRET_NAME =
      "devworkspace-merged-git-credentials";

  private static final Map<String, String> SEARCH_LABELS =
      ImmutableMap.of(
          "app.kubernetes.io/part-of", "che.eclipse.org",
          "app.kubernetes.io/component", "scm-personal-access-token");

  @BeforeMethod
  public void setUp() throws InfrastructureException {
    configurator =
        new CredentialsSecretConfigurator(
            cheServerKubernetesClientFactory, personalAccessTokenManager);

    serverMock = new KubernetesServer(true, true);
    serverMock.before();
    KubernetesClient client = spy(serverMock.getClient());
    when(cheServerKubernetesClientFactory.create()).thenReturn(client);

    namespaceResolutionContext =
        new NamespaceResolutionContext(TEST_WORKSPACE_ID, TEST_USER_ID, TEST_USERNAME);
  }

  @Test
  public void shouldStorePersonalAccessToken() throws Exception {
    // given
    serverMock
        .getClient()
        .secrets()
        .inNamespace(TEST_NAMESPACE_NAME)
        .create(
            new SecretBuilder()
                .withNewMetadata()
                .withName(PAT_SECRET_NAME)
                .withLabels(SEARCH_LABELS)
                .withAnnotations(Map.of("che.eclipse.org/scm-url", "test-url"))
                .endMetadata()
                .build());
    // when
    configurator.configure(namespaceResolutionContext, TEST_NAMESPACE_NAME);

    // then
    verify(personalAccessTokenManager).store(eq("test-url"));
  }

  @Test
  public void doNothingWhenSecretAlreadyStored() throws Exception {
    // given
    serverMock
        .getClient()
        .secrets()
        .inNamespace(TEST_NAMESPACE_NAME)
        .create(
            new SecretBuilder()
                .withNewMetadata()
                .withName(MERGED_GIT_CREDENTIALS_SECRET_NAME)
                .endMetadata()
                .withData(
                    Map.of(
                        "credentials", Base64.getEncoder().encodeToString("test-token".getBytes())))
                .build());

    serverMock
        .getClient()
        .secrets()
        .inNamespace(TEST_NAMESPACE_NAME)
        .create(
            new SecretBuilder()
                .withNewMetadata()
                .withName(PAT_SECRET_NAME)
                .withLabels(SEARCH_LABELS)
                .withAnnotations(Map.of("che.eclipse.org/scm-url", "test-url"))
                .endMetadata()
                .withData(
                    Map.of("token", Base64.getEncoder().encodeToString("test-token".getBytes())))
                .build());
    // when
    configurator.configure(namespaceResolutionContext, TEST_NAMESPACE_NAME);

    // then
    verify(personalAccessTokenManager, never()).store(anyString());
  }
}
