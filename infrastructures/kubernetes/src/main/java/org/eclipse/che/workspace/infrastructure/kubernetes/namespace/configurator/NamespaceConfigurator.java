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

import io.fabric8.kubernetes.api.model.Secret;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.NamespaceProvisioner;

/**
 * Configures user's namespace after provisioning in {@link NamespaceProvisioner} with whatever is
 * needed. Such as creating user profile and preferences {@link Secret} in user namespace.
 *
 * @author Pavol Baran
 */
public interface NamespaceConfigurator {

  /**
   * Configures user's namespace after provisioning.
   *
   * @param namespaceResolutionContext users namespace context
   * @throws InfrastructureException when any error occurs
   */
  void configure(NamespaceResolutionContext namespaceResolutionContext, String namespaceName)
      throws InfrastructureException;
}
