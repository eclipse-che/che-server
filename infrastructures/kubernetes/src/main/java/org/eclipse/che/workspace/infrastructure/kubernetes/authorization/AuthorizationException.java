/*
 * Copyright (c) 2012-2023 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.workspace.infrastructure.kubernetes.authorization;

import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.RuntimeInfrastructure;

/**
 * An exception thrown by {@link RuntimeInfrastructure} and related components. Indicates that a
 * user is not authorized to use Che.
 *
 * @author Anatolii Bazko
 */
public class AuthorizationException extends InfrastructureException {
  public AuthorizationException(String message) {
    super(message);
  }
}
