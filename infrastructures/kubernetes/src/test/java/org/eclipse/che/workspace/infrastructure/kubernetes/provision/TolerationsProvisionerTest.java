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

import static org.testng.Assert.assertTrue;

import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.workspace.infrastructure.kubernetes.environment.KubernetesEnvironment;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class TolerationsProvisionerTest {

  @Mock private RuntimeIdentity runtimeId;
  private KubernetesEnvironment k8sEnv;

  private TolerationsProvisioner provisioner;

  @BeforeMethod
  public void setUp() {
    k8sEnv = KubernetesEnvironment.builder().build();
  }

  @Test
  public void shouldOmitEmptyTolerations() throws Exception {
    // given
    k8sEnv.addPod(createPod("pod"));
    k8sEnv.addPod(createPod("pod2"));

    provisioner = new TolerationsProvisioner();

    // when
    provisioner.provision(k8sEnv, runtimeId);

    // then
    for (Pod pod : k8sEnv.getPodsCopy().values()) {
      assertTrue(pod.getSpec().getTolerations().isEmpty());
    }
  }

  private Pod createPod(String podName) {
    return new PodBuilder()
        .withNewMetadata()
        .withName(podName)
        .endMetadata()
        .withNewSpec()
        .withTolerations()
        .withInitContainers(new ContainerBuilder().build())
        .withContainers(new ContainerBuilder().build(), new ContainerBuilder().build())
        .endSpec()
        .build();
  }
}
