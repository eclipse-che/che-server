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
package org.eclipse.che.api.factory.server.scm;

import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmConfigurationPersistenceException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.commons.annotation.Nullable;

public interface GitUserDataFetcher {

  /**
   * Retrieve a {@link GitUserData} object from concrete scm provider. If OAuthProvider is not
   * configured, then personal access token should be taken into account.
   *
   * @param namespaceName - the user's namespace name.
   * @return - {@link GitUserData} object.
   * @throws ScmUnauthorizedException - in case if user is not authorized che server to create a new
   *     token. Further user interaction is needed before calling this method next time.
   * @throws ScmCommunicationException - Some unexpected problem occurred during communication with
   *     scm provider.
   * @throws ScmConfigurationPersistenceException - problem occurred during communication with
   *     permanent storage.
   */
  GitUserData fetchGitUserData(@Nullable String namespaceName)
      throws ScmUnauthorizedException, ScmCommunicationException,
          ScmConfigurationPersistenceException, ScmItemNotFoundException, ScmBadRequestException;

  /**
   * Retrieve a {@link GitUserData} object from concrete scm provider. If OAuthProvider is not
   * configured, then personal access token should be taken into account.
   *
   * @return - {@link GitUserData} object.
   * @throws ScmUnauthorizedException - in case if user is not authorized che server to create a new
   *     token. Further user interaction is needed before calling this method next time.
   * @throws ScmCommunicationException - Some unexpected problem occurred during communication with
   *     scm provider.
   * @throws ScmConfigurationPersistenceException - problem occurred during communication with
   *     permanent storage.
   */
  default GitUserData fetchGitUserData()
      throws ScmUnauthorizedException, ScmCommunicationException,
          ScmConfigurationPersistenceException, ScmItemNotFoundException, ScmBadRequestException {
    return fetchGitUserData(null);
  }
}
