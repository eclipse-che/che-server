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
import org.eclipse.che.api.factory.server.scm.exception.ScmConfigurationPersistenceException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.factory.server.scm.exception.UnknownScmProviderException;
import org.eclipse.che.api.factory.server.scm.exception.UnsatisfiedScmPreconditionException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.subject.Subject;

/** Manages {@link PersonalAccessToken}s in Che's permanent storage. */
public interface PersonalAccessTokenManager {
  /**
   * Fetches a new {@link PersonalAccessToken} token from scm provider and save it in permanent
   * storage for further usage.
   *
   * @param cheUser
   * @param scmServerUrl
   * @return personal access token
   * @throws UnsatisfiedScmPreconditionException - storage preconditions aren't met.
   * @throws ScmConfigurationPersistenceException - problem occurred during communication with
   *     permanent storage.
   * @throws ScmUnauthorizedException - scm authorization required.
   * @throws ScmCommunicationException - problem occurred during communication with scm provider.
   * @throws UnknownScmProviderException - scm provider is unknown.
   */
  PersonalAccessToken fetchAndSave(Subject cheUser, String scmServerUrl)
      throws UnsatisfiedScmPreconditionException, ScmConfigurationPersistenceException,
          ScmUnauthorizedException, ScmCommunicationException, UnknownScmProviderException;

  /**
   * Gets {@link PersonalAccessToken} from permanent storage.
   *
   * @param cheUser Che user object
   * @param scmServerUrl Git provider endpoint
   * @return personal access token
   * @throws ScmConfigurationPersistenceException - problem occurred during communication with
   *     permanent storage.
   * @throws ScmCommunicationException - problem occurred during communication with SCM server.
   */
  Optional<PersonalAccessToken> get(Subject cheUser, String scmServerUrl)
      throws ScmConfigurationPersistenceException, ScmCommunicationException;

  /**
   * Gets {@link PersonalAccessToken} from permanent storage.
   *
   * @param scmServerUrl Git provider endpoint
   * @throws ScmConfigurationPersistenceException - problem occurred during communication with
   *     permanent storage.
   * @throws ScmUnauthorizedException - scm authorization required.
   * @throws ScmCommunicationException - problem occurred during communication with scm provider.
   * @throws UnknownScmProviderException - scm provider is unknown.
   * @throws UnsatisfiedScmPreconditionException - storage preconditions aren't met.
   */
  PersonalAccessToken get(String scmServerUrl)
      throws ScmConfigurationPersistenceException, ScmUnauthorizedException,
          ScmCommunicationException, UnknownScmProviderException,
          UnsatisfiedScmPreconditionException;

  /**
   * Gets {@link PersonalAccessToken} from permanent storage for the given OAuth provider name. It
   * is useful when OAuth provider is not configured and Git provider endpoint is unknown. {@code
   * scmServerUrl} can be provided as an additional clause.
   *
   * @param cheUser Che user object
   * @param oAuthProviderName OAuth provider name to get token for
   * @param scmServerUrl Git provider endpoint
   * @return personal access token
   * @throws ScmConfigurationPersistenceException - problem occurred during communication with
   *     permanent storage.
   * @throws ScmCommunicationException - problem occurred during communication with SCM server.
   */
  Optional<PersonalAccessToken> get(
      Subject cheUser, String oAuthProviderName, @Nullable String scmServerUrl)
      throws ScmConfigurationPersistenceException, ScmCommunicationException;

  /**
   * Gets {@link PersonalAccessToken} from permanent storage. If the token is not found try to fetch
   * it from scm provider and save it in a permanent storage and set (update) git-credentials.
   *
   * @param scmServerUrl Git provider endpoint
   */
  PersonalAccessToken getAndStore(String scmServerUrl)
      throws ScmCommunicationException, ScmConfigurationPersistenceException,
          UnknownScmProviderException, UnsatisfiedScmPreconditionException,
          ScmUnauthorizedException;

  /** Refresh a personal access token. */
  void forceRefreshPersonalAccessToken(String scmServerUrl)
      throws UnsatisfiedScmPreconditionException, ScmConfigurationPersistenceException,
          ScmUnauthorizedException, ScmCommunicationException, UnknownScmProviderException;

  /**
   * Set or update git-credentials with {@link PersonalAccessToken} from permanent storage.
   *
   * @param scmServerUrl Git provider endpoint
   * @throws UnsatisfiedScmPreconditionException - storage preconditions aren't met.
   * @throws ScmConfigurationPersistenceException - problem occurred during communication with
   *     permanent storage.
   * @throws ScmCommunicationException - problem occurred during communication with scm provider.
   * @throws ScmUnauthorizedException - scm authorization required.
   */
  void storeGitCredentials(String scmServerUrl)
      throws UnsatisfiedScmPreconditionException, ScmConfigurationPersistenceException,
          ScmCommunicationException, ScmUnauthorizedException;

  /**
   * Store {@link PersonalAccessToken} in permanent storage.
   *
   * @param token personal access token
   * @throws UnsatisfiedScmPreconditionException - storage preconditions aren't met.
   * @throws ScmConfigurationPersistenceException - problem occurred during communication with
   *     permanent storage.
   */
  void store(PersonalAccessToken token)
      throws UnsatisfiedScmPreconditionException, ScmConfigurationPersistenceException;
}
