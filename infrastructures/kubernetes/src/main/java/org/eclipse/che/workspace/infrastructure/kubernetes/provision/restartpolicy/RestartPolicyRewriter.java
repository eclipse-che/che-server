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
package org.eclipse.che.workspace.infrastructure.kubernetes.provision.restartpolicy;

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.PodSpec;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.workspace.server.model.impl.WarningImpl;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.annotation.Traced;
import org.eclipse.che.commons.tracing.TracingTags;
import org.eclipse.che.workspace.infrastructure.kubernetes.Warnings;
import org.eclipse.che.workspace.infrastructure.kubernetes.environment.KubernetesEnvironment;
import org.eclipse.che.workspace.infrastructure.kubernetes.environment.KubernetesEnvironment.PodData;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.ConfigurationProvisioner;

/**
 * Rewrites restart policy to supported one - 'Never'.
 *
 * @author Alexander Garagatyi
 */
public class RestartPolicyRewriter implements ConfigurationProvisioner {

  static final String DEFAULT_RESTART_POLICY = "Never";

  @Override
  @Traced
  public void provision(KubernetesEnvironment k8sEnv, RuntimeIdentity identity)
      throws InfrastructureException {

    TracingTags.WORKSPACE_ID.set(identity::getWorkspaceId);

    for (PodData podConfig : k8sEnv.getPodsData().values()) {
      final String podName = podConfig.getMetadata().getName();
      final PodSpec podSpec = podConfig.getSpec();
      rewriteRestartPolicy(podSpec, podName, k8sEnv);
    }
  }

  private void rewriteRestartPolicy(PodSpec podSpec, String podName, KubernetesEnvironment env) {
    final String restartPolicy = podSpec.getRestartPolicy();

    if (restartPolicy != null && !DEFAULT_RESTART_POLICY.equalsIgnoreCase(restartPolicy)) {
      final String warnMsg =
          format(
              Warnings.RESTART_POLICY_SET_TO_NEVER_WARNING_MESSAGE_FMT,
              restartPolicy,
              podName,
              DEFAULT_RESTART_POLICY);
      env.addWarning(new WarningImpl(Warnings.RESTART_POLICY_SET_TO_NEVER_WARNING_CODE, warnMsg));
    }
    podSpec.setRestartPolicy(DEFAULT_RESTART_POLICY);
  }
}
