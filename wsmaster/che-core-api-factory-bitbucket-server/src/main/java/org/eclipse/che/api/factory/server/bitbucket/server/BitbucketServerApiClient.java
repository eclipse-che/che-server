/*
 * Copyright (c) 2012-2025 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.factory.server.bitbucket.server;

import java.util.List;
import java.util.Set;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;

/** Bitbucket Server API client. */
public interface BitbucketServerApiClient {
  /**
   * @param bitbucketServerUrl
   * @return - true if client is connected to the given bitbucket server.
   */
  boolean isConnected(String bitbucketServerUrl);

  /**
   * @param token token to authorise the user request.
   * @return - authenticated {@link BitbucketUser}.
   */
  BitbucketUser getUser(String token)
      throws ScmItemNotFoundException, ScmUnauthorizedException, ScmCommunicationException;

  /**
   * @return Retrieve the authenticated {@link BitbucketUser} using an OAuth token.
   * @return - authenticated {@link BitbucketUser}.
   */
  BitbucketUser getUser()
      throws ScmItemNotFoundException, ScmUnauthorizedException, ScmCommunicationException;

  /**
   * @return Retrieve a list of {@link BitbucketUser}. Only authenticated users may call this
   *     resource.
   * @throws ScmBadRequestException
   * @throws ScmUnauthorizedException
   * @throws ScmCommunicationException
   */
  List<BitbucketUser> getUsers()
      throws ScmBadRequestException, ScmUnauthorizedException, ScmCommunicationException;

  /**
   * @return Retrieve a list of {@link BitbucketUser}, optionally run through provided filters. Only
   *     authenticated users may call this resource.
   * @throws ScmBadRequestException
   * @throws ScmUnauthorizedException
   * @throws ScmCommunicationException
   */
  List<BitbucketUser> getUsers(String filter)
      throws ScmBadRequestException, ScmUnauthorizedException, ScmCommunicationException;

  /**
   * Modify an access token for the user according to the given request. Any fields not specified
   * will not be altered
   *
   * @param tokenId - the token id
   * @throws ScmItemNotFoundException
   * @throws ScmUnauthorizedException
   * @throws ScmCommunicationException
   */
  void deletePersonalAccessTokens(String tokenId)
      throws ScmItemNotFoundException, ScmUnauthorizedException, ScmCommunicationException;

  /**
   * Create an access token for the user according to the given request.
   *
   * @param tokenName
   * @param permissions
   * @return
   * @throws ScmBadRequestException
   * @throws ScmUnauthorizedException
   * @throws ScmCommunicationException
   */
  BitbucketPersonalAccessToken createPersonalAccessTokens(String tokenName, Set<String> permissions)
      throws ScmBadRequestException,
          ScmUnauthorizedException,
          ScmCommunicationException,
          ScmItemNotFoundException;

  /**
   * Get all personal access tokens associated with the given user
   *
   * @return
   * @throws ScmItemNotFoundException
   * @throws ScmUnauthorizedException
   * @throws ScmBadRequestException
   * @throws ScmCommunicationException
   */
  List<BitbucketPersonalAccessToken> getPersonalAccessTokens()
      throws ScmItemNotFoundException, ScmUnauthorizedException, ScmCommunicationException;

  /**
   * @param tokenId - bitbucket personal access token id.
   * @param oauthToken - bitbucket oauth token.
   * @return - Bitbucket personal access token.
   * @throws ScmCommunicationException
   */
  BitbucketPersonalAccessToken getPersonalAccessToken(String tokenId, String oauthToken)
      throws ScmItemNotFoundException, ScmUnauthorizedException, ScmCommunicationException;
}
