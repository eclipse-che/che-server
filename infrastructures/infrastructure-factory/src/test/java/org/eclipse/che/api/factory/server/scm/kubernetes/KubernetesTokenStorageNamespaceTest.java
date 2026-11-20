/*
 * Copyright (c) 2012-2026 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.factory.server.scm.kubernetes;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMixedDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.mockwebserver.Context;
import io.fabric8.mockwebserver.MockWebServer;
import io.fabric8.mockwebserver.ServerRequest;
import io.fabric8.mockwebserver.ServerResponse;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.ScmPersonalAccessTokenFetcher;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.api.server.impls.KubernetesNamespaceMetaImpl;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespace;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespaceFactory;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Holds the namespace the token and the git credentials of a refresh are written to, with the real
 * {@link KubernetesPersonalAccessTokenManager} and {@link KubernetesGitCredentialManager} working
 * against a Kubernetes API server, and only the SCM provider mocked away.
 *
 * <p>The job that refreshes the OAuth tokens of the running workspaces, {@code
 * OAuthTokenRefresher}, is the one caller that has no user of its own: it names the user and the
 * namespace it works on outright. Everything the refresh writes has to land in that namespace, and
 * nowhere else, and the thread it ran on must be left speaking for nobody.
 */
@Listeners(MockitoTestNGListener.class)
public class KubernetesTokenStorageNamespaceTest {

  private static final String NAMESPACE = "named-namespace";
  private static final String OTHER_NAMESPACE = "other-namespace";
  private static final String USER_ID = "user123";
  private static final String SCM_URL = "https://gitlab.com";

  @Mock private CheServerKubernetesClientFactory cheServerKubernetesClientFactory;
  @Mock private KubernetesNamespaceFactory namespaceFactory;
  @Mock private ScmPersonalAccessTokenFetcher scmPersonalAccessTokenFetcher;

  private KubernetesMockServer kubernetesMockServer;
  private KubernetesClient kubernetesClient;

  private KubernetesPersonalAccessTokenManager personalAccessTokenManager;

  @BeforeMethod
  public void setUp() throws Exception {
    final Map<ServerRequest, Queue<ServerResponse>> responses = new HashMap<>();
    kubernetesMockServer =
        new KubernetesMockServer(
            new Context(),
            new MockWebServer(),
            responses,
            new KubernetesMixedDispatcher(responses),
            true);
    kubernetesMockServer.init();
    kubernetesClient = kubernetesMockServer.createClient();
    for (String namespace : List.of(NAMESPACE, OTHER_NAMESPACE)) {
      kubernetesClient
          .namespaces()
          .resource(
              new NamespaceBuilder().withNewMetadata().withName(namespace).endMetadata().build())
          .create();
    }

    lenient().when(cheServerKubernetesClientFactory.create()).thenReturn(kubernetesClient);
    lenient()
        .when(cheServerKubernetesClientFactory.create(nullable(String.class)))
        .thenReturn(kubernetesClient);
    // The namespace is the one named in the context, so only the two reads of a known namespace
    // are answered. #list, which resolves the namespace from the subject, is left unstubbed on
    // purpose: a fall back to it returns no namespace at all and fails the run.
    lenient()
        .when(namespaceFactory.fetchNamespace(eq(NAMESPACE)))
        .thenReturn(Optional.of(new KubernetesNamespaceMetaImpl(NAMESPACE)));
    lenient()
        .when(namespaceFactory.access(nullable(String.class), eq(NAMESPACE)))
        .thenReturn(
            new KubernetesNamespace(
                cheServerKubernetesClientFactory, Runnable::run, NAMESPACE, null));

    KubernetesGitCredentialManager gitCredentialManager =
        new KubernetesGitCredentialManager(namespaceFactory, cheServerKubernetesClientFactory);
    personalAccessTokenManager =
        new KubernetesPersonalAccessTokenManager(
            namespaceFactory,
            cheServerKubernetesClientFactory,
            scmPersonalAccessTokenFetcher,
            gitCredentialManager);
  }

  @AfterMethod
  public void cleanUp() {
    kubernetesMockServer.destroy();
    EnvironmentContext.reset();
  }

  @Test
  public void shouldWriteTheRefreshedTokenAndCredentialsToTheNamespaceNamedInTheContext()
      throws Exception {
    // given
    when(scmPersonalAccessTokenFetcher.refreshPersonalAccessToken(
            any(Subject.class), eq(SCM_URL), eq(NAMESPACE)))
        .thenReturn(
            new PersonalAccessToken(
                SCM_URL,
                "gitlab",
                USER_ID,
                null,
                "jondoe",
                "oauth2-abcde",
                "tid-24",
                "refreshed-token",
                "refreshed-refresh-token",
                3600));

    // when
    personalAccessTokenManager.forceRefreshPersonalAccessToken(SCM_URL, USER_ID, NAMESPACE);

    // then the token lands in the namespace of the context
    List<Secret> tokenSecrets = secretsOf(NAMESPACE, "scm-personal-access-token");
    assertEquals(tokenSecrets.size(), 1);
    Secret tokenSecret = tokenSecrets.get(0);
    assertEquals(decode(tokenSecret.getData().get("token")), "refreshed-token");
    assertEquals(decode(tokenSecret.getData().get("refresh-token")), "refreshed-refresh-token");
    assertEquals(
        tokenSecret.getMetadata().getAnnotations().get("che.eclipse.org/che-userid"), USER_ID);

    // and so do the git credentials
    List<Secret> credentialSecrets = secretsOf(NAMESPACE, "workspace-secret");
    assertEquals(credentialSecrets.size(), 1);
    assertEquals(
        decode(credentialSecrets.get(0).getData().get("credentials")),
        "https://oauth2:refreshed-token@gitlab.com");

    // and nothing is written anywhere else
    assertTrue(allSecretsOf(OTHER_NAMESPACE).isEmpty());
    verify(namespaceFactory, never()).list();
    // and the thread the refresh ran on is left as it was found
    assertEquals(EnvironmentContext.getCurrent().getSubject(), Subject.ANONYMOUS);
  }

  private List<Secret> secretsOf(String namespace, String component) {
    return kubernetesClient
        .secrets()
        .inNamespace(namespace)
        .withLabels(
            Map.of(
                "app.kubernetes.io/part-of",
                "che.eclipse.org",
                "app.kubernetes.io/component",
                component))
        .list()
        .getItems();
  }

  private List<Secret> allSecretsOf(String namespace) {
    return kubernetesClient.secrets().inNamespace(namespace).list().getItems();
  }

  private static String decode(String value) {
    return new String(Base64.getDecoder().decode(value), UTF_8);
  }
}
