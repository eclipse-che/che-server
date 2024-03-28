/*
 * Copyright (c) 2012-2024 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.factory.server.scm;

import org.eclipse.che.api.factory.server.scm.exception.ScmConfigurationPersistenceException;
import org.eclipse.che.api.factory.server.scm.exception.UnsatisfiedScmPreconditionException;

public interface GitCredentialManager {
  /**
   * Propagates git credentials in format: "username:<oauth token>" if the token is Personal Access
   * Token or "oauth2:<oauth token> if oAuth token.
   *
   * @param personalAccessToken
   * @throws UnsatisfiedScmPreconditionException - some storage preconditions aren't met.
   * @throws ScmConfigurationPersistenceException
   */
  void createOrReplace(PersonalAccessToken personalAccessToken)
      throws UnsatisfiedScmPreconditionException, ScmConfigurationPersistenceException;
}
