/*
 * Copyright (c) 2012-2026 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.security.oauth;

import com.google.inject.AbstractModule;
import org.eclipse.che.security.oauth.kubernetes.KubernetesUserWorkspaceUrlProvider;

/** Binds the Kubernetes backed implementations of the OAuth SPI. */
public class KubernetesOAuthModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(UserWorkspaceUrlProvider.class).to(KubernetesUserWorkspaceUrlProvider.class);
  }
}
