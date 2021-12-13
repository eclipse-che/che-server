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

import static org.eclipse.che.workspace.infrastructure.kubernetes.Constants.DEV_WORKSPACE_MOUNT_LABEL;
import static org.eclipse.che.workspace.infrastructure.kubernetes.Constants.DEV_WORKSPACE_WATCH_SECRET_LABEL;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.ssh.server.SshManager;
import org.eclipse.che.api.ssh.server.model.impl.SshPairImpl;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;
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

  @Mock private KubernetesClientFactory clientFactory;
  @Mock private SshManager sshManager;

  @InjectMocks private SshKeysConfigurator sshKeysConfigurator;

  private KubernetesServer kubernetesServer;
  private NamespaceResolutionContext context;

  private final SshPairImpl sshPair =
      new SshPairImpl(USER_ID, "vcs", "github.com", "public-key", "private-key");

  @BeforeMethod
  public void setUp() throws InfrastructureException, NotFoundException, ServerException {
    context = new NamespaceResolutionContext(null, USER_ID, USER_NAME);
    kubernetesServer = new KubernetesServer(true, true);
    kubernetesServer.before();

    when(sshManager.getPairs(USER_ID, "vcs")).thenReturn(Collections.singletonList(sshPair));
    when(clientFactory.create()).thenReturn(kubernetesServer.getClient());
  }

  @AfterMethod
  public void cleanUp() {
    kubernetesServer.getClient().secrets().inNamespace(USER_NAMESPACE).delete();
    kubernetesServer.after();
  }

  @Test
  public void shouldCreateSSHKeysSecret() throws InfrastructureException {
    sshKeysConfigurator.configure(context, USER_NAMESPACE);
    List<Secret> secrets =
        kubernetesServer
            .getClient()
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
        kubernetesServer
            .getClient()
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
