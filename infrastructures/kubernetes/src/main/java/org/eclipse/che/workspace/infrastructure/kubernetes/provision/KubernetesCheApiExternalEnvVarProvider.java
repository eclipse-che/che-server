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

import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.provision.env.CheApiExternalEnvVarProvider;
import org.eclipse.che.commons.lang.Pair;

/**
 * Provides env variable to Kubernetes machine with url of Che API.
 *
 * @author Mykhailo Kuznietsov
 */
public class KubernetesCheApiExternalEnvVarProvider implements CheApiExternalEnvVarProvider {

  private final String cheServerEndpoint;

  @Inject
  public KubernetesCheApiExternalEnvVarProvider(@Named("che.api") String cheServerEndpoint) {
    this.cheServerEndpoint = cheServerEndpoint;
  }

  @Override
  public Pair<String, String> get(RuntimeIdentity runtimeIdentity) throws InfrastructureException {
    return Pair.of(CHE_API_EXTERNAL_VARIABLE, cheServerEndpoint);
  }
}
