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
package org.eclipse.che.workspace.infrastructure.kubernetes.provision;

import static java.util.Collections.emptyMap;

import io.fabric8.kubernetes.api.model.PodSpec;
import java.util.Map;
import javax.inject.Inject;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.workspace.infrastructure.kubernetes.environment.KubernetesEnvironment;

/** Provisions node selector into workspace pod spec. */
public class NodeSelectorProvisioner implements ConfigurationProvisioner {

  private final Map<String, String> nodeSelectorAttributes;

  @Inject
  public NodeSelectorProvisioner() {
    this.nodeSelectorAttributes = emptyMap();
  }

  @Override
  public void provision(KubernetesEnvironment k8sEnv, RuntimeIdentity identity)
      throws InfrastructureException {
    if (!nodeSelectorAttributes.isEmpty()) {
      k8sEnv
          .getPodsData()
          .values()
          .forEach(d -> d.getSpec().setNodeSelector(nodeSelectorAttributes));
    }
  }

  public void provision(PodSpec podSpec) {
    if (!nodeSelectorAttributes.isEmpty()) {
      podSpec.setNodeSelector(nodeSelectorAttributes);
    }
  }
}
