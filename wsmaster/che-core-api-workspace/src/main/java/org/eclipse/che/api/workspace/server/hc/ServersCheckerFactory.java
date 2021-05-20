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
package org.eclipse.che.api.workspace.server.hc;

import java.util.Map;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.core.model.workspace.runtime.Server;

/**
 * Creates {@link ServersChecker} for server readiness checking.
 *
 * @author Alexander Garagatyi
 * @author Sergii Leshchenko
 */
public interface ServersCheckerFactory {
  ServersChecker create(
      RuntimeIdentity runtimeIdentity, String machineName, Map<String, ? extends Server> servers);
}
