/*
 * Copyright (c) 2012-2022 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.workspace.infrastructure.kubernetes;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;

/**
 * This {@link KubernetesClientFactory} ensures that we use `che` ServiceAccount and not related to
 * any workspace. It always provides client with default {@link Config}. It's useful for operations
 * that needs permissions of `che` SA, such as operations inside `che` namespace (like creating a
 * ConfigMaps for Gateway router) or some cluster-wide actions (like labeling the namespaces).
 */
@Singleton
public class CheServerKubernetesClientFactory extends KubernetesClientFactory {

  @Inject
  public CheServerKubernetesClientFactory(KubernetesClientConfigFactory configBuilder) {
    super(configBuilder);
  }

  /** @param workspaceId ignored */
  @Override
  public KubernetesClient create(String workspaceId) throws InfrastructureException {
    return create();
  }

  /**
   * creates an instance of {@link KubernetesClient} that is meant to be used on Che installation
   * namespace
   */
  @Override
  public KubernetesClient create() throws InfrastructureException {
    return super.create();
  }

  @Override
  protected Config buildConfig(Config config, String workspaceId) {
    return config;
  }
}
