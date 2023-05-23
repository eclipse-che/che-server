/*
 * Copyright (c) 2012-2023 Red Hat, Inc.
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

import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.AbstractWorkspaceServiceAccount.CREDENTIALS_SECRET_NAME;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;

/**
 * This {@link NamespaceConfigurator} ensures that Secret {@link
 * org.eclipse.che.workspace.infrastructure.kubernetes.namespace.AbstractWorkspaceServiceAccount#CREDENTIALS_SECRET_NAME}
 * is present in the Workspace namespace.
 */
@Singleton
public class CredentialsSecretConfigurator implements NamespaceConfigurator {

  private final CheServerKubernetesClientFactory cheServerKubernetesClientFactory;

  @Inject
  public CredentialsSecretConfigurator(
      CheServerKubernetesClientFactory cheServerKubernetesClientFactory) {
    this.cheServerKubernetesClientFactory = cheServerKubernetesClientFactory;
  }

  @Override
  public void configure(NamespaceResolutionContext namespaceResolutionContext, String namespaceName)
      throws InfrastructureException {
    var client = cheServerKubernetesClientFactory.create();
    if (client.secrets().inNamespace(namespaceName).withName(CREDENTIALS_SECRET_NAME).get()
        == null) {
      Secret secret =
          new SecretBuilder()
              .withType("opaque")
              .withNewMetadata()
              .withName(CREDENTIALS_SECRET_NAME)
              .endMetadata()
              .build();
      client.secrets().inNamespace(namespaceName).create(secret);
    }
  }
}
