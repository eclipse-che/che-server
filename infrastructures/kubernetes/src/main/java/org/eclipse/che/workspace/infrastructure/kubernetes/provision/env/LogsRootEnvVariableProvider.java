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
package org.eclipse.che.workspace.infrastructure.kubernetes.provision.env;

import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.provision.env.EnvVarProvider;
import org.eclipse.che.commons.lang.Pair;

/**
 * Add env variable to machines with path to root folder of workspace logs.
 *
 * @author Anton Korneta
 */
public class LogsRootEnvVariableProvider implements EnvVarProvider {

  /** Environment variable that points to root folder of projects inside machine */
  public static final String WORKSPACE_LOGS_ROOT_ENV_VAR = "CHE_WORKSPACE_LOGS_ROOT__DIR";

  private String logsRootPath;

  @Inject
  public LogsRootEnvVariableProvider(@Named("che.workspace.logs.root_dir") String logsRootPath) {
    this.logsRootPath = logsRootPath;
  }

  @Override
  public Pair<String, String> get(RuntimeIdentity identity) throws InfrastructureException {
    return Pair.of(WORKSPACE_LOGS_ROOT_ENV_VAR, logsRootPath);
  }
}
