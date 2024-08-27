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

import java.util.Optional;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.factory.server.scm.exception.UnknownScmProviderException;
import org.eclipse.che.commons.lang.Pair;
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
      throws ScmUnauthorizedException, ScmCommunicationException, UnknownScmProviderException;

  /**
   * Refresh a PersonalAccessToken.
   *
   * @throws ScmUnauthorizedException - in case if user are not authorized che server to create new
   *     token. Further user interaction is needed before calling next time this method.
   * @throws ScmCommunicationException - Some unexpected problem occurred during communication with
   *     scm provider.
   */
  PersonalAccessToken refreshPersonalAccessToken(Subject cheUser, String scmServerUrl)
      throws ScmUnauthorizedException, ScmCommunicationException, UnknownScmProviderException;

  /**
   * Checks whether the provided personal access token is valid and has expected scope of
   * permissions.
   *
   * @deprecated use {@link #isValid(PersonalAccessTokenParams)} instead.
   * @param personalAccessToken - personal access token to check.
   * @return - empty optional if {@link PersonalAccessTokenFetcher} is not able to confirm or deny
   *     that token is valid or {@link Boolean} value if it can.
   * @throws ScmUnauthorizedException - in case if user did not authorized che server to create new
   *     token. Further user interaction is needed before calling next time this method.
   * @throws ScmCommunicationException - Some unexpected problem occurred during communication with
   *     scm provider.
   */
  @Deprecated
  Optional<Boolean> isValid(PersonalAccessToken personalAccessToken)
      throws ScmCommunicationException, ScmUnauthorizedException;

  /**
   * Checks whether the provided personal access token is valid by fetching user info from the scm
   * provider. Also checks whether the token has expected scope of permissions if the provider API
   * supports such request.
   *
   * @return - Optional with a pair of boolean value and scm username. The boolean value is true if
   *     the token has expected scope of permissions, false if the token scopes does not match the
   *     expected ones. Empty optional if {@link PersonalAccessTokenFetcher} is not able to confirm
   *     or deny that token is valid.
   * @throws ScmCommunicationException - problem occurred during communication with SCM server.
   */
  Optional<Pair<Boolean, String>> isValid(PersonalAccessTokenParams params)
      throws ScmCommunicationException;
}
