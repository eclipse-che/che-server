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
package org.eclipse.che.api.factory.server.scm;

import org.eclipse.che.api.factory.server.scm.exception.ScmConfigurationPersistenceException;
import org.eclipse.che.api.factory.server.scm.exception.UnsatisfiedScmPreconditionException;
import org.eclipse.che.commons.annotation.Nullable;

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

  /**
   * The same as {@link #createOrReplace(PersonalAccessToken)}, for a caller that works on the
   * namespace of a user other than the one of the current request and names that namespace itself.
   *
   * @param personalAccessToken the token to propagate
   * @param namespaceName the namespace to write the credentials to, or {@code null} to take the
   *     namespace of the user of the current request
   */
  void createOrReplace(PersonalAccessToken personalAccessToken, @Nullable String namespaceName)
      throws UnsatisfiedScmPreconditionException, ScmConfigurationPersistenceException;
}
