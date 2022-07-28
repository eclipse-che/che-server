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

import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.AppsAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.EditReplacePatchDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import java.util.UUID;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.environment.KubernetesEnvironment;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class AsyncStoragePodInterceptorTest {

  private static final String WORKSPACE_ID = UUID.randomUUID().toString();
  private static final String NAMESPACE = UUID.randomUUID().toString();

  @Mock private KubernetesEnvironment kubernetesEnvironment;
  @Mock private RuntimeIdentity identity;
  @Mock private KubernetesClientFactory clientFactory;
  @Mock private KubernetesClient kubernetesClient;
  @Mock private RollableScalableResource<Deployment> deploymentResource;
  @Mock private PodResource<Pod> podResource;
  @Mock private MixedOperation mixedOperation;
  @Mock private MixedOperation mixedOperationPod;
  @Mock private NonNamespaceOperation namespaceOperation;
  @Mock private NonNamespaceOperation namespacePodOperation;
  @Mock private EditReplacePatchDeletable<Deployment> deletable;
  @Mock private AppsAPIGroupDSL apps;

  private AsyncStoragePodInterceptor asyncStoragePodInterceptor;

  @BeforeMethod
  public void setUp() {
    asyncStoragePodInterceptor = new AsyncStoragePodInterceptor(clientFactory);
  }

  @Test
  public void shouldDoNothingIfNotCommonStrategy() throws Exception {
    AsyncStoragePodInterceptor asyncStoragePodInterceptor =
        new AsyncStoragePodInterceptor(clientFactory);
    asyncStoragePodInterceptor.intercept(kubernetesEnvironment, identity);
    verifyNoMoreInteractions(clientFactory);
    verifyNoMoreInteractions(identity);
  }
}
