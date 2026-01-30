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
package org.eclipse.che.workspace.infrastructure.kubernetes.namespace.configurator;

import static org.eclipse.che.commons.lang.StringUtils.trimEnd;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Base64;
import javax.inject.Inject;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;

/**
 * This {@link NamespaceConfigurator} ensures that the SSH key Secret from the Workspace namespace
 * does not contain the `ssh_config` key in its data. If the key is present in the Secret data,
 * remove it in order to avoid mount collisions from the `git-ssh-config` ConfigMap and Create a
 * dedicated ConfigMap to mount SSH Config. See
 * https://github.com/eclipse-che/che-dashboard/pull/1443
 */
public class SshConfigConfigurator implements NamespaceConfigurator {
  private final CheServerKubernetesClientFactory cheServerKubernetesClientFactory;

  @Inject
  public SshConfigConfigurator(CheServerKubernetesClientFactory cheServerKubernetesClientFactory) {
    this.cheServerKubernetesClientFactory = cheServerKubernetesClientFactory;
  }

  @Override
  public void configure(NamespaceResolutionContext namespaceResolutionContext, String namespaceName)
      throws InfrastructureException {
    String configKeyName = "ssh_config";
    String config =
        "host *\n"
            + "  IdentityFile /etc/ssh/dwo_ssh_key\n"
            + "  StrictHostKeyChecking = no\n"
            + "\n"
            + "Include /etc/ssh/ssh_config.d/*.conf";

    KubernetesClient client = cheServerKubernetesClientFactory.create();

    Secret secret = client.secrets().inNamespace(namespaceName).withName("git-ssh-key").get();
    if (secret != null && secret.getData().containsKey(configKeyName)) {
      if (trimEnd(new String(Base64.getDecoder().decode(secret.getData().get(configKeyName))), '\n')
          .equals(config)) {
        return;
      }
      secret.getData().put(configKeyName, Base64.getEncoder().encodeToString(config.getBytes()));
      client.secrets().inNamespace(namespaceName).resource(secret).patch();
    }
  }
}
