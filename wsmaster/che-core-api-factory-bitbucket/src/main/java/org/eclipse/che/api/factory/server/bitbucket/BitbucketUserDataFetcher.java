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
package org.eclipse.che.api.factory.server.bitbucket;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.api.factory.server.scm.GitUserData;
import org.eclipse.che.api.factory.server.scm.GitUserDataFetcher;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.security.oauth.OAuthAPI;

/**
 * Bitbucket user data retriever. TODO: extends {@code AbstractGitUserDataFetcher} when we support
 * personal access tokens for BitBucket.
 */
public class BitbucketUserDataFetcher implements GitUserDataFetcher {
  private final String apiEndpoint;
  private final OAuthAPI oAuthAPI;

  /** Bitbucket API client. */
  private final BitbucketApiClient bitbucketApiClient;

  /** Name of this OAuth provider as found in OAuthAPI. */
  private static final String OAUTH_PROVIDER_NAME = "bitbucket";

  /** Collection of OAuth scopes required to make integration with Bitbucket work. */
  public static final Set<String> DEFAULT_TOKEN_SCOPES = ImmutableSet.of("repo");

  @Inject
  public BitbucketUserDataFetcher(@Named("che.api") String apiEndpoint, OAuthAPI oAuthAPI) {
    this(apiEndpoint, oAuthAPI, new BitbucketApiClient());
  }

  /** Constructor used for testing only. */
  public BitbucketUserDataFetcher(
      String apiEndpoint, OAuthAPI oAuthAPI, BitbucketApiClient bitbucketApiClient) {
    this.apiEndpoint = apiEndpoint;
    this.oAuthAPI = oAuthAPI;
    this.bitbucketApiClient = bitbucketApiClient;
  }

  @Override
  public GitUserData fetchGitUserData() throws ScmUnauthorizedException, ScmCommunicationException {
    OAuthToken oAuthToken;
    try {
      oAuthToken = oAuthAPI.getOrRefreshToken(OAUTH_PROVIDER_NAME);
      // Find the user associated to the OAuth token by querying the Bitbucket API.
      BitbucketUser user = bitbucketApiClient.getUser(oAuthToken.getToken());
      BitbucketUserEmail emailResponse = bitbucketApiClient.getEmail(oAuthToken.getToken());
      String email =
          emailResponse.getValues().length > 0 ? emailResponse.getValues()[0].getEmail() : "";
      return new GitUserData(user.getDisplayName(), email);
    } catch (UnauthorizedException e) {
      Subject cheSubject = EnvironmentContext.getCurrent().getSubject();
      throw new ScmUnauthorizedException(
          cheSubject.getUserName()
              + " is not authorized in "
              + OAUTH_PROVIDER_NAME
              + " OAuth provider.",
          OAUTH_PROVIDER_NAME,
          "2.0",
          getLocalAuthenticateUrl());
    } catch (NotFoundException
        | ServerException
        | ForbiddenException
        | BadRequestException
        | ScmItemNotFoundException
        | ScmBadRequestException
        | ConflictException e) {
      throw new ScmCommunicationException(e.getMessage(), e);
    }
  }

  private String getLocalAuthenticateUrl() {
    return apiEndpoint
        + "/oauth/authenticate?oauth_provider="
        + OAUTH_PROVIDER_NAME
        + "&scope="
        + Joiner.on(',').join(DEFAULT_TOKEN_SCOPES)
        + "&request_method=POST&signature_method=rsa";
  }
}
