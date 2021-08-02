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
package org.eclipse.che.workspace.infrastructure.kubernetes.provision;

import static com.google.common.base.Strings.isNullOrEmpty;

import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.provision.env.CheApiInternalEnvVarProvider;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.lang.Pair;

/**
 * Provides env variable to Kubernetes machine with url of Che API.
 *
 * @author Mykhailo Kuznietsov
 */
public class KubernetesCheApiInternalEnvVarProvider implements CheApiInternalEnvVarProvider {

  private final String cheServerEndpoint;

  @Inject
  public KubernetesCheApiInternalEnvVarProvider(
      @Nullable @Named("che.api.internal") String cheServerEndpoint) {
    this.cheServerEndpoint = cheServerEndpoint;
  }

  @Override
  public Pair<String, String> get(RuntimeIdentity runtimeIdentity) throws InfrastructureException {
    if (isNullOrEmpty(this.cheServerEndpoint)) {
      return null;
    }
    return Pair.of(CHE_API_INTERNAL_VARIABLE, cheServerEndpoint);
  }
}
