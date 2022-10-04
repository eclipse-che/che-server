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

import static java.util.Collections.emptyList;

import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Toleration;
import java.util.List;
import javax.inject.Inject;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.inject.ConfigurationException;
import org.eclipse.che.workspace.infrastructure.kubernetes.environment.KubernetesEnvironment;

/** Provisions tolerations into workspace pod spec. */
public class TolerationsProvisioner implements ConfigurationProvisioner {

  private final List<Toleration> tolerations;

  @Inject
  public TolerationsProvisioner() throws ConfigurationException {
    this.tolerations = emptyList();
  }

  @Override
  public void provision(KubernetesEnvironment k8sEnv, RuntimeIdentity identity)
      throws InfrastructureException {
    if (!tolerations.isEmpty()) {
      k8sEnv.getPodsData().values().forEach(d -> d.getSpec().setTolerations(tolerations));
    }
  }

  public void provision(PodSpec podSpec) {
    if (!tolerations.isEmpty()) {
      podSpec.setTolerations(tolerations);
    }
  }
}
