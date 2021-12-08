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
package org.eclipse.che.api.factory.server.github;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
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
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenFetcher;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.security.oauth.OAuthAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** GitHub OAuth token retriever. */
public class GithubPersonalAccessTokenFetcher implements PersonalAccessTokenFetcher {

  private static final Logger LOG = LoggerFactory.getLogger(GithubPersonalAccessTokenFetcher.class);
  private final String apiEndpoint;
  private final OAuthAPI oAuthAPI;

  /** GitHub API client. */
  private final GithubApiClient githubApiClient;

  /** Name of this OAuth provider as found in OAuthAPI. */
  private static final String OAUTH_PROVIDER_NAME = "github";

  /** Collection of OAuth scopes required to make integration with GitHub work. */
  public static final Set<String> DEFAULT_TOKEN_SCOPES = ImmutableSet.of("repo");

  /**
   * Map of OAuth GitHub scopes where each key is a scope and its value is the parent scope. The
   * parent scope includes all of its children scopes. This map is used when determining if a token
   * has the required scopes. See
   * https://docs.github.com/en/developers/apps/building-oauth-apps/scopes-for-oauth-apps#available-scopes
   */
  private static final Map<String, String> SCOPE_MAP =
      ImmutableMap.<String, String>builderWithExpectedSize(35)
          .put("repo", "repo")
          .put("repo:status", "repo")
          .put("repo_deployment", "repo")
          .put("public_repo", "repo")
          .put("repo:invite", "repo")
          .put("security_events", "repo")
          //
          .put("workflow", "workflow")
          //
          .put("write:packages", "write:packages")
          .put("read:packages", "write:packages")
          //
          .put("delete:packages", "delete:packages")
          //
          .put("admin:org", "admin:org")
          .put("write:org", "admin:org")
          .put("read:org", "admin:org")
          //
          .put("admin:public_key", "admin:public_key")
          .put("write:public_key", "admin:public_key")
          .put("read:public_key", "admin:public_key")
          //
          .put("admin:repo_hook", "admin:repo_hook")
          .put("write:repo_hook", "admin:repo_hook")
          .put("read:repo_hook", "admin:repo_hook")
          //
          .put("admin:org_hook", "admin:org_hook")
          //
          .put("gist", "gist")
          //
          .put("notifications", "notifications")
          //
          .put("user", "user")
          .put("read:user", "user")
          .put("user:email", "user")
          .put("user:follow", "user")
          //
          .put("delete_repo", "delete_repo")
          //
          .put("write:discussion", "write:discussion")
          .put("read:discussion", "write:discussion")
          //
          .put("admin:enterprise", "admin:enterprise")
          .put("manage_billing:enterprise", "admin:enterprise")
          .put("read:enterprise", "admin:enterprise")
          //
          .put("admin:gpg_key", "admin:gpg_key")
          .put("write:gpg_key", "admin:gpg_key")
          .put("read:gpg_key", "admin:gpg_key")
          .build();

  @Inject
  public GithubPersonalAccessTokenFetcher(@Named("che.api") String apiEndpoint, OAuthAPI oAuthAPI) {
    this(apiEndpoint, oAuthAPI, new GithubApiClient());
  }

  /**
   * Constructor used for testing only.
   *
   * @param apiEndpoint
   * @param oAuthAPI
   * @param githubApiClient
   */
  GithubPersonalAccessTokenFetcher(
      String apiEndpoint, OAuthAPI oAuthAPI, GithubApiClient githubApiClient) {
    this.apiEndpoint = apiEndpoint;
    this.oAuthAPI = oAuthAPI;
    this.githubApiClient = githubApiClient;
  }

  @Override
  public PersonalAccessToken fetchPersonalAccessToken(Subject cheSubject, String scmServerUrl)
      throws ScmUnauthorizedException, ScmCommunicationException {
    OAuthToken oAuthToken;

    if (githubApiClient == null || !githubApiClient.isConnected(scmServerUrl)) {
      LOG.debug("not a  valid url {} for current fetcher ", scmServerUrl);
      return null;
    }
    try {
      oAuthToken = oAuthAPI.getToken(OAUTH_PROVIDER_NAME);
      // Find the user associated to the OAuth token by querying the GitHub API.
      GithubUser user = githubApiClient.getUser(oAuthToken.getToken());
      PersonalAccessToken token =
          new PersonalAccessToken(
              scmServerUrl,
              cheSubject.getUserId(),
              user.getLogin(),
              Long.toString(user.getId()),
              NameGenerator.generate(OAUTH_2_PREFIX, 5),
              NameGenerator.generate("id-", 5),
              oAuthToken.getToken());
      Optional<Boolean> valid = isValid(token);
      if (valid.isEmpty()) {
        throw new ScmCommunicationException(
            "Unable to verify if current token is a valid GitHub token.  Token's scm-url needs to be '"
                + GithubApiClient.GITHUB_SERVER
                + "' and was '"
                + token.getScmProviderUrl()
                + "'");
      } else if (!valid.get()) {
        throw new ScmCommunicationException(
            "Current token doesn't have the necessary privileges. Please make sure Che app scopes are correct and containing at least: "
                + DEFAULT_TOKEN_SCOPES.toString());
      }
      return token;
    } catch (UnauthorizedException e) {
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
      LOG.error(e.getMessage());
      throw new ScmCommunicationException(e.getMessage(), e);
    }
  }

  @Override
  public Optional<Boolean> isValid(PersonalAccessToken personalAccessToken)
      throws ScmCommunicationException, ScmUnauthorizedException {
    if (!githubApiClient.isConnected(personalAccessToken.getScmProviderUrl())) {
      LOG.debug("not a valid url {} for current fetcher ", personalAccessToken.getScmProviderUrl());
      return Optional.empty();
    }

    if (personalAccessToken.getScmTokenName() != null
        && personalAccessToken.getScmTokenName().startsWith(OAUTH_2_PREFIX)) {
      try {
        String[] scopes = githubApiClient.getTokenScopes(personalAccessToken.getToken());
        return Optional.of(containsScopes(scopes, DEFAULT_TOKEN_SCOPES));
      } catch (ScmItemNotFoundException | ScmCommunicationException | ScmBadRequestException e) {
        LOG.error(e.getMessage(), e);
        throw new ScmCommunicationException(e.getMessage(), e);
      }
    } else {
      // No REST API for PAT-s in Github found yet. Just try to do some action.
      try {
        GithubUser user = githubApiClient.getUser(personalAccessToken.getToken());
        if (personalAccessToken.getScmUserId().equals(Long.toString(user.getId()))) {
          return Optional.of(Boolean.TRUE);
        } else {
          return Optional.of(Boolean.FALSE);
        }
      } catch (ScmItemNotFoundException | ScmCommunicationException | ScmBadRequestException e) {
        return Optional.of(Boolean.FALSE);
      }
    }
  }

  /**
   * Checks if the tokenScopes array contains the requiredScopes.
   *
   * @param tokenScopes Scopes from token
   * @param requiredScopes Mandatory scopes
   * @return If all mandatory scopes are contained in the token's scopes
   */
  boolean containsScopes(String[] tokenScopes, Set<String> requiredScopes) {
    Arrays.sort(tokenScopes);
    // We need check that the token has the required minimal scopes.  The scopes can be normalized
    // by GitHub, so we need to be careful for sub-scopes being included in parent scopes.
    for (String requiredScope : requiredScopes) {
      String parentScope = SCOPE_MAP.get(requiredScope);
      if (parentScope == null) {
        // requiredScope is not recognized as a GitHub scope, so just skip it.
        continue;
      }
      if (Arrays.binarySearch(tokenScopes, parentScope) < 0
          && Arrays.binarySearch(tokenScopes, requiredScope) < 0) {
        return false;
      }
    }
    return true;
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
