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

import java.util.Optional;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.core.*;
import org.eclipse.che.api.factory.server.scm.exception.*;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.security.oauth.OAuthAPI;

/**
 * Abstraction to fetch git user data from the specific git provider using OAuth 2.0 or personal
 * access.
 *
 * @author Anatolii Bazko
 */
public abstract class AbstractGitUserDataFetcher implements GitUserDataFetcher {
  protected final String oAuthProviderName;
  protected final PersonalAccessTokenManager personalAccessTokenManager;
  protected final OAuthAPI oAuthTokenFetcher;

  public AbstractGitUserDataFetcher(
      String oAuthProviderName,
      PersonalAccessTokenManager personalAccessTokenManager,
      OAuthAPI oAuthTokenFetcher) {
    this.oAuthProviderName = oAuthProviderName;
    this.personalAccessTokenManager = personalAccessTokenManager;
    this.oAuthTokenFetcher = oAuthTokenFetcher;
  }

  public GitUserData fetchGitUserData()
      throws ScmUnauthorizedException, ScmCommunicationException,
          ScmConfigurationPersistenceException, ScmItemNotFoundException, ScmBadRequestException {
    Subject cheSubject = EnvironmentContext.getCurrent().getSubject();
    try {
      OAuthToken oAuthToken = oAuthTokenFetcher.getToken(oAuthProviderName);
      return fetchGitUserDataWithOAuthToken(oAuthToken);
    } catch (UnauthorizedException e) {
      throw new ScmUnauthorizedException(
          cheSubject.getUserName()
              + " is not authorized in "
              + oAuthProviderName
              + " OAuth provider.",
          oAuthProviderName,
          "2.0",
          getLocalAuthenticateUrl());
    } catch (NotFoundException e) {
      Optional<PersonalAccessToken> personalAccessToken =
          personalAccessTokenManager.get(cheSubject, oAuthProviderName, null);
      if (personalAccessToken.isPresent()) {
        return fetchGitUserDataWithPersonalAccessToken(personalAccessToken.get());
      }
      throw new ScmCommunicationException(
          "There are no tokes for the user " + cheSubject.getUserId());
    } catch (ServerException | ForbiddenException | BadRequestException | ConflictException e) {
      throw new ScmCommunicationException(e.getMessage(), e);
    }
  }

  protected abstract GitUserData fetchGitUserDataWithOAuthToken(OAuthToken oAuthToken)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException;

  protected abstract GitUserData fetchGitUserDataWithPersonalAccessToken(
      PersonalAccessToken personalAccessToken)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException;

  protected abstract String getLocalAuthenticateUrl();
}
