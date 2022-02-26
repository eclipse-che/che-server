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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.client.Config;
import java.util.Collections;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.workspace.infrastructure.kubernetes.cache.KubernetesRuntimeStateCache;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespaceFactory;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;

@Listeners(MockitoTestNGListener.class)
public class KubernetesInfrastructureTest {

  @Mock private KubernetesClientFactory factory;
  private KubernetesInfrastructure infra;

  @BeforeMethod
  public void setup() {
    infra =
        new KubernetesInfrastructure(
            mock(EventService.class),
            mock(KubernetesRuntimeContextFactory.class),
            Collections.emptySet(),
            mock(KubernetesRuntimeStateCache.class),
            mock(KubernetesNamespaceFactory.class),
            factory);

    when(factory.getDefaultConfig()).thenReturn(mock(Config.class));
  }
}
