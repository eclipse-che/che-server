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
import org.eclipse.che.api.factory.server.scm.exception.*;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;

/**
 * Abstraction to fetch git user data from the specific git provider using OAuth 2.0 or personal
 * access.
 *
 * @author Anatolii Bazko
 */
public abstract class AbstractGitUserDataFetcher implements GitUserDataFetcher {
  protected final String oAuthProviderName;
  private final String oAuthProviderUrl;
  protected final PersonalAccessTokenManager personalAccessTokenManager;

  public AbstractGitUserDataFetcher(
      String oAuthProviderName,
      String oAuthProviderUrl,
      PersonalAccessTokenManager personalAccessTokenManager) {
    this.oAuthProviderName = oAuthProviderName;
    this.oAuthProviderUrl = oAuthProviderUrl;
    this.personalAccessTokenManager = personalAccessTokenManager;
  }

  public GitUserData fetchGitUserData()
      throws ScmUnauthorizedException, ScmCommunicationException,
          ScmConfigurationPersistenceException, ScmItemNotFoundException, ScmBadRequestException {
    Subject cheSubject = EnvironmentContext.getCurrent().getSubject();
    Optional<PersonalAccessToken> tokenOptional =
        personalAccessTokenManager.get(cheSubject, oAuthProviderName, null);
    if (tokenOptional.isPresent()) {
      return fetchGitUserDataWithPersonalAccessToken(tokenOptional.get());
    } else {
      Optional<PersonalAccessToken> oAuthTokenOptional =
          personalAccessTokenManager.get(cheSubject, oAuthProviderUrl);
      if (oAuthTokenOptional.isPresent()) {
        return fetchGitUserDataWithOAuthToken(oAuthTokenOptional.get().getToken());
      }
    }
    throw new ScmCommunicationException(
        "There are no tokes for the user " + cheSubject.getUserId());
  }

  protected abstract GitUserData fetchGitUserDataWithOAuthToken(String token)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException,
          ScmUnauthorizedException;

  protected abstract GitUserData fetchGitUserDataWithPersonalAccessToken(
      PersonalAccessToken personalAccessToken)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException,
          ScmUnauthorizedException;

  protected abstract String getLocalAuthenticateUrl();
}
