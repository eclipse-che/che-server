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
package org.eclipse.che.workspace.infrastructure.openshift;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.openshift.client.OpenShiftClient;
import javax.inject.Inject;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientConfigFactory;

/**
 * This {@link OpenShiftClientFactory} ensures that we use `che` ServiceAccount and not related to
 * any workspace. It always provides client with default {@link Config}. It's useful for operations
 * that needs permissions of `che` SA, such as operations inside `che` namespace (like projects) or
 * some cluster-wide actions (like labeling the namespaces).
 *
 * <p>See also {@link
 * org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory}
 */
public class CheServerOpenshiftClientFactory extends OpenShiftClientFactory {
  @Inject
  public CheServerOpenshiftClientFactory(KubernetesClientConfigFactory configBuilder) {
    super(configBuilder);
  }

  @Override
  protected Config buildConfig(Config config, String workspaceId) throws InfrastructureException {
    return config;
  }

  @Override
  public OpenShiftClient createOC(String workspaceId) throws InfrastructureException {
    return super.createOC();
  }

  @Override
  public OpenShiftClient createOC() throws InfrastructureException {
    return super.createOC();
  }
}
