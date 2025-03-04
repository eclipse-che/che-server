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

import static org.eclipse.che.workspace.infrastructure.kubernetes.Constants.DEV_WORKSPACE_MOUNT_LABEL;
import static org.eclipse.che.workspace.infrastructure.kubernetes.Constants.DEV_WORKSPACE_WATCH_SECRET_LABEL;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMixedDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.mockwebserver.Context;
import io.fabric8.mockwebserver.ServerRequest;
import io.fabric8.mockwebserver.ServerResponse;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import okhttp3.mockwebserver.MockWebServer;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.ssh.server.SshManager;
import org.eclipse.che.api.ssh.server.model.impl.SshPairImpl;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class SshKeysConfiguratorTest {

  private static final String USER_ID = "user-id";
  private static final String USER_NAME = "user-name";
  private static final String USER_NAMESPACE = "user-namespace";

  @Mock private CheServerKubernetesClientFactory cheServerKubernetesClientFactory;
  @Mock private SshManager sshManager;

  @InjectMocks private SshKeysConfigurator sshKeysConfigurator;
  private KubernetesMockServer kubernetesMockServer;
  private KubernetesClient kubernetesClient;

  private NamespaceResolutionContext context;

  private final SshPairImpl sshPair =
      new SshPairImpl(USER_ID, "vcs", "github.com", "public-key", "private-key");

  @BeforeMethod
  public void setUp() throws InfrastructureException, NotFoundException, ServerException {
    context = new NamespaceResolutionContext(null, USER_ID, USER_NAME);
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
    when(sshManager.getPairs(USER_ID, "vcs")).thenReturn(Collections.singletonList(sshPair));
    when(cheServerKubernetesClientFactory.create()).thenReturn(kubernetesClient);
  }

  @AfterMethod
  public void cleanUp() {
    kubernetesClient.secrets().inNamespace(USER_NAMESPACE).delete();
    kubernetesMockServer.destroy();
  }

  @Test
  public void shouldCreateSSHKeysSecret() throws InfrastructureException {
    sshKeysConfigurator.configure(context, USER_NAMESPACE);
    List<Secret> secrets =
        kubernetesClient
            .secrets()
            .inNamespace(USER_NAMESPACE)
            .withLabels(
                Map.of(
                    DEV_WORKSPACE_MOUNT_LABEL, "true",
                    DEV_WORKSPACE_WATCH_SECRET_LABEL, "true"))
            .list()
            .getItems();
    assertEquals(secrets.size(), 1);
    assertEquals(secrets.get(0).getMetadata().getName(), "che-git-ssh-key");
    assertEquals(secrets.get(0).getData().size(), 3);
    assertEquals(
        new String(Base64.getDecoder().decode(secrets.get(0).getData().get("github.com"))),
        "private-key");
    assertEquals(
        new String(Base64.getDecoder().decode(secrets.get(0).getData().get("github.com.pub"))),
        "public-key");
    assertEquals(
        new String(Base64.getDecoder().decode(secrets.get(0).getData().get("ssh_config"))),
        "host github.com\n"
            + "IdentityFile /etc/ssh/github.com\n"
            + "StrictHostKeyChecking = no\n\n");
  }

  @Test
  public void shouldNotCreateSSHKeysSecretFromBadSecret() throws Exception {
    SshPairImpl sshPairLocal =
        new SshPairImpl(USER_ID, "vcs", "%sd$$$", "public-key", "private-key");
    when(sshManager.getPairs(USER_ID, "vcs")).thenReturn(List.of(sshPairLocal));
    sshKeysConfigurator.configure(context, USER_NAMESPACE);
    List<Secret> secrets =
        kubernetesClient
            .secrets()
            .inNamespace(USER_NAMESPACE)
            .withLabels(
                Map.of(
                    DEV_WORKSPACE_MOUNT_LABEL, "true",
                    DEV_WORKSPACE_WATCH_SECRET_LABEL, "true"))
            .list()
            .getItems();
    assertEquals(secrets.size(), 0);
  }
}
