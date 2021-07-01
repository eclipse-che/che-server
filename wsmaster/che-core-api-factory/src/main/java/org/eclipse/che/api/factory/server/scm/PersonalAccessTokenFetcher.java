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
package org.eclipse.che.api.factory.server.scm;

import java.util.Optional;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.commons.subject.Subject;

public interface PersonalAccessTokenFetcher {

  /** Prefix for token names indication it is OAuth token (to differentiate from PAT-s) */
  String OAUTH_2_PREFIX = "oauth2-";

  /**
   * Retrieve new PersonalAccessToken from concrete scm provider
   *
   * @param cheUser
   * @param scmServerUrl
   * @return - personal access token. Must return {@code null} if scmServerUrl is not applicable for
   *     the current fetcher.
   * @throws ScmUnauthorizedException - in case if user are not authorized che server to create new
   *     token. Further user interaction is needed before calling next time this method.
   * @throws ScmCommunicationException - Some unexpected problem occurred during communication with
   *     scm provider.
   */
  PersonalAccessToken fetchPersonalAccessToken(Subject cheUser, String scmServerUrl)
      throws ScmUnauthorizedException, ScmCommunicationException;

  /**
   * Checks whether the provided personal access token is valid and has expected scope of
   * permissions.
   *
   * @param personalAccessToken - personal access token to check.
   * @return - empty optional if {@link PersonalAccessTokenFetcher} is not able to confirm or deny
   *     that token is valid or {@link Boolean} value if it can.
   * @throws ScmUnauthorizedException - in case if user did not authorized che server to create new
   *     token. Further user interaction is needed before calling next time this method.
   * @throws ScmCommunicationException - Some unexpected problem occurred during communication with
   *     scm provider.
   */
  Optional<Boolean> isValid(PersonalAccessToken personalAccessToken)
      throws ScmCommunicationException, ScmUnauthorizedException;
}
