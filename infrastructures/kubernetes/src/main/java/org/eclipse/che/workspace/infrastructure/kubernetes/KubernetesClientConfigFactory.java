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

import static com.google.common.base.Strings.isNullOrEmpty;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.annotation.Nullable;

/**
 * This class allows customizing the Kubernetes {@link Config} according to the current context
 * (workspace ID, current user).
 *
 * @author David Festal
 */
public class KubernetesClientConfigFactory {
  private final String masterUrl;
  private final Boolean doTrustCerts;

  public KubernetesClientConfigFactory(String masterUrl, Boolean doTrustCerts) {
    this.masterUrl = masterUrl;
    this.doTrustCerts = doTrustCerts;
  }

  /**
   * Builds the Kubernetes {@link Config} object based on a default {@link Config} object and an
   * optional workspace Id.
   */
  public Config buildConfig(Config defaultConfig, @Nullable String workspaceId)
      throws InfrastructureException {
    return defaultConfig;
  }

  /**
   * Builds the default Kubernetes {@link Config} that will be the base configuration to create
   * per-workspace configurations.
   */
  protected Config buildDefaultConfig() {
    ConfigBuilder configBuilder = new ConfigBuilder();
    if (!isNullOrEmpty(masterUrl)) {
      configBuilder.withMasterUrl(masterUrl);
    }

    if (doTrustCerts != null) {
      configBuilder.withTrustCerts(doTrustCerts);
    }

    return configBuilder.build();
  }
  /**
   * Returns true if implementation personalizes config to the current subject, otherwise returns
   * false if default config is always used.
   */
  public boolean isPersonalized() {
    return false;
  }
}
