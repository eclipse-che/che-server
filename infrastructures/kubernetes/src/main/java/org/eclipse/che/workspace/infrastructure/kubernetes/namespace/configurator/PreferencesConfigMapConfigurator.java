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
package org.eclipse.che.workspace.infrastructure.kubernetes.namespace.configurator;

import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.AbstractWorkspaceServiceAccount.PREFERENCES_CONFIGMAP_NAME;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import javax.inject.Inject;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;

/**
 * This {@link NamespaceConfigurator} ensures that ConfigMap {@link
 * org.eclipse.che.workspace.infrastructure.kubernetes.namespace.AbstractWorkspaceServiceAccount#PREFERENCES_CONFIGMAP_NAME}
 * is present in the Workspace namespace.
 */
public class PreferencesConfigMapConfigurator implements NamespaceConfigurator {

  private final KubernetesClientFactory clientFactory;

  @Inject
  public PreferencesConfigMapConfigurator(KubernetesClientFactory clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public void configure(NamespaceResolutionContext namespaceResolutionContext, String namespaceName)
      throws InfrastructureException {
    var client = clientFactory.create();
    if (client.configMaps().inNamespace(namespaceName).withName(PREFERENCES_CONFIGMAP_NAME).get()
        == null) {
      ConfigMap configMap =
          new ConfigMapBuilder()
              .withNewMetadata()
              .withName(PREFERENCES_CONFIGMAP_NAME)
              .endMetadata()
              .build();
      client.configMaps().inNamespace(namespaceName).create(configMap);
    }
  }
}
