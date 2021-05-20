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
package org.eclipse.che.workspace.infrastructure.kubernetes.namespace.pvc;

import static java.lang.String.format;

import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * Provides implementation of {@link WorkspaceVolumesStrategy} for configured value.
 *
 * @author Anton Korneta
 */
@Singleton
public class WorkspaceVolumeStrategyProvider implements Provider<WorkspaceVolumesStrategy> {

  private final WorkspaceVolumesStrategy volumeStrategy;

  @Inject
  public WorkspaceVolumeStrategyProvider(
      @Named("che.infra.kubernetes.pvc.strategy") String strategy,
      Map<String, WorkspaceVolumesStrategy> strategies) {
    final WorkspaceVolumesStrategy volumeStrategy = strategies.get(strategy);
    if (volumeStrategy != null) {
      this.volumeStrategy = volumeStrategy;
    } else {
      throw new IllegalArgumentException(
          format("Unsupported PVC strategy '%s' configured", strategy));
    }
  }

  @Override
  public WorkspaceVolumesStrategy get() {
    return volumeStrategy;
  }
}
