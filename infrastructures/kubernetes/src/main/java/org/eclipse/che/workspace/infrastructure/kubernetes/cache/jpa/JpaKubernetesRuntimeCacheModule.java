/*
 * Copyright (c) 2012-2024 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.workspace.infrastructure.kubernetes.cache.jpa;

import com.google.inject.AbstractModule;
import org.eclipse.che.workspace.infrastructure.kubernetes.cache.KubernetesMachineCache;
import org.eclipse.che.workspace.infrastructure.kubernetes.cache.KubernetesRuntimeStateCache;

/** @author Sergii Leshchenko */
public class JpaKubernetesRuntimeCacheModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(KubernetesRuntimeStateCache.class).to(JpaKubernetesRuntimeStateCache.class);
    bind(KubernetesMachineCache.class).to(JpaKubernetesMachineCache.class);
  }
}
