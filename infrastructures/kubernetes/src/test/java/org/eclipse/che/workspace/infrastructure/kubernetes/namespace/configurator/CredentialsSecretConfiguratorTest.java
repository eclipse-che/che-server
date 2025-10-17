/*
 * Copyright (c) 2012-2025 Red Hat, Inc.
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMixedDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.mockwebserver.Context;
import io.fabric8.mockwebserver.MockWebServer;
import io.fabric8.mockwebserver.ServerRequest;
import io.fabric8.mockwebserver.ServerResponse;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class CredentialsSecretConfiguratorTest {

  private NamespaceConfigurator configurator;

  @Mock private CheServerKubernetesClientFactory cheServerKubernetesClientFactory;
  @Mock private PersonalAccessTokenManager personalAccessTokenManager;
  private KubernetesMockServer kubernetesMockServer;
  private KubernetesClient kubernetesClient;

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
    final Map<ServerRequest, Queue<ServerResponse>> responses = new HashMap<>();
    kubernetesMockServer =
        new KubernetesMockServer(
            new Context(),
            new MockWebServer(),
            responses,
            new KubernetesMixedDispatcher(responses),
            true);
    kubernetesMockServer.init();
    kubernetesClient = spy(kubernetesMockServer.createClient());

    KubernetesClient client = spy(kubernetesClient);
    when(cheServerKubernetesClientFactory.create()).thenReturn(client);

    namespaceResolutionContext =
        new NamespaceResolutionContext(TEST_WORKSPACE_ID, TEST_USER_ID, TEST_USERNAME);
  }

  @AfterMethod
  public void cleanUp() {
    kubernetesMockServer.destroy();
  }

  @Test
  public void shouldStorePersonalAccessToken() throws Exception {
    // given
    kubernetesClient
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
    verify(personalAccessTokenManager).storeGitCredentials(eq("test-url"));
  }

  @Test
  public void shouldRemovePersonalAccessToken() throws Exception {
    // given
    doThrow(new ScmCommunicationException("test error", 495))
        .when(personalAccessTokenManager)
        .storeGitCredentials(eq("test-url"));
    kubernetesClient
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
    try {
      configurator.configure(namespaceResolutionContext, TEST_NAMESPACE_NAME);
    } catch (Exception e) {
      assertTrue(e instanceof InfrastructureException);
      assertEquals(e.getMessage(), "test error");
    }

    // then
    verify(personalAccessTokenManager).remove(eq("test-url"));
  }

  @Test
  public void doNothingWhenSecretAlreadyStored() throws Exception {
    // given
    kubernetesClient
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

    kubernetesClient
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
    verify(personalAccessTokenManager, never()).storeGitCredentials(anyString());
  }
}
