/*
 * Copyright (c) 2012-2022 Red Hat, Inc.
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

import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;

public interface GitUserDataFetcher {

  /**
   * Retrieve a {@link GitUserData} object from concrete scm provider
   *
   * @return - {@link GitUserData} object.
   * @throws ScmUnauthorizedException - in case if user is not authorized che server to create a new
   *     token. Further user interaction is needed before calling this method next time.
   * @throws ScmCommunicationException - Some unexpected problem occurred during communication with
   *     scm provider.
   */
  GitUserData fetchGitUserData() throws ScmUnauthorizedException, ScmCommunicationException;
}
