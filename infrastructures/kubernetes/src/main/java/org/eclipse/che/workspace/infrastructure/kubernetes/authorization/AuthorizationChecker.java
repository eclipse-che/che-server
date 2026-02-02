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
package org.eclipse.che.workspace.infrastructure.kubernetes.authorization;

import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.subject.Subject;

/** This {@link AuthorizationChecker} checks if user is allowed to use Che. */
public interface AuthorizationChecker {

  boolean isAuthorized(Subject subject) throws InfrastructureException;
}
