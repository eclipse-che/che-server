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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.commons.annotation.Nullable;

/**
 * This Factory provides {@link GatewayRouteConfigGenerator} instances, so implementation using
 * these can stay Gateway technology agnostic.
 */
@Singleton
public class GatewayRouteConfigGeneratorFactory {

  private final String clusterDomain;

  @Inject
  public GatewayRouteConfigGeneratorFactory(
      @Nullable @Named("che.infra.kubernetes.cluster_domain") String clusterDomain) {
    this.clusterDomain = clusterDomain;
  }

  public GatewayRouteConfigGenerator create() {
    return new TraefikGatewayRouteConfigGenerator(clusterDomain);
  }
}
