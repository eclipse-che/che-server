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
package org.eclipse.che.workspace.infrastructure.kubernetes.server.external;

import static java.util.Collections.emptyMap;
import static org.eclipse.che.workspace.infrastructure.kubernetes.server.external.IngressServerExposer.SERVICE_NAME_PLACEHOLDER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.che.api.core.model.workspace.config.ServerConfig;
import org.eclipse.che.api.workspace.server.model.impl.ServerConfigImpl;
import org.eclipse.che.workspace.infrastructure.kubernetes.environment.KubernetesEnvironment;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/** @author Serhii Leshchenko */
@Listeners(MockitoTestNGListener.class)
public class IngressServerExposerTest {
  @Mock private ExternalServiceExposureStrategy serviceExposureStrategy;

  @BeforeMethod
  public void setUp() throws Exception {
    when(serviceExposureStrategy.getExternalHost(any(), any())).thenReturn("host");
    when(serviceExposureStrategy.getExternalPath(any(), any())).thenReturn("path");
  }

  @Test
  public void shouldReplaceServerNamePlaceholders() {
    // given
    Map<String, String> annotations = new HashMap<>();
    annotations.put("ssl", "true");
    annotations.put("websocket-service", SERVICE_NAME_PLACEHOLDER);

    IngressServerExposer<KubernetesEnvironment> exposer =
        new IngressServerExposer<>(serviceExposureStrategy, annotations, null, "");

    KubernetesEnvironment env = KubernetesEnvironment.builder().build();

    Map<String, ServerConfig> externalServers = new HashMap<>();
    externalServers.put("ide", new ServerConfigImpl("6543", "http", "/", emptyMap()));

    // when
    exposer.expose(env, "editor", "ide", "server123", new ServicePort(), externalServers);

    // then
    Collection<Ingress> ingresses = env.getIngresses().values();
    assertEquals(ingresses.size(), 1);
    Ingress ingress = ingresses.iterator().next();
    assertEquals(ingress.getMetadata().getAnnotations().get("ssl"), "true");
    assertEquals(ingress.getMetadata().getAnnotations().get("websocket-service"), "ide");
  }
}
