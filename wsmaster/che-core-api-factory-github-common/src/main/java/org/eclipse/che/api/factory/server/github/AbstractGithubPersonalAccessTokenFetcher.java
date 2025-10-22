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
package org.eclipse.che.api.factory.server.github;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.core.*;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenFetcher;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenParams;
import org.eclipse.che.api.factory.server.scm.exception.*;
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.commons.lang.Pair;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.security.oauth.OAuthAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** GitHub OAuth token retriever. */
public abstract class AbstractGithubPersonalAccessTokenFetcher
    implements PersonalAccessTokenFetcher {

  private static final Logger LOG =
      LoggerFactory.getLogger(AbstractGithubPersonalAccessTokenFetcher.class);
  private static final String OAUTH_PROVIDER_NAME = "github";
  private final String apiEndpoint;
  private final OAuthAPI oAuthAPI;

  /** GitHub API client. */
  private final GithubApiClient githubApiClient;

  /** Name of this OAuth provider as found in OAuthAPI. */
  private final String providerName;

  /** Collection of OAuth scopes required to make integration with GitHub work. */
  public static final Set<String> DEFAULT_TOKEN_SCOPES =
      ImmutableSet.of("repo", "user:email", "read:user", "read:org", "workflow");

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

  /**
   * Constructor used for testing only.
   *
   * @param apiEndpoint
   * @param oAuthAPI
   * @param githubApiClient
   */
  AbstractGithubPersonalAccessTokenFetcher(
      String apiEndpoint, OAuthAPI oAuthAPI, GithubApiClient githubApiClient, String providerName) {
    this.apiEndpoint = apiEndpoint;
    this.oAuthAPI = oAuthAPI;
    this.githubApiClient = githubApiClient;
    this.providerName = providerName;
  }

  public PersonalAccessToken refreshPersonalAccessToken(Subject cheSubject, String scmServerUrl)
      throws ScmUnauthorizedException, ScmCommunicationException, UnknownScmProviderException {
    // Tokens generated via GitHub OAuth app do not have an expiration date, so we don't need to
    // refresh them.
    return fetchPersonalAccessToken(cheSubject, scmServerUrl);
  }

  @Override
  public PersonalAccessToken fetchPersonalAccessToken(Subject cheSubject, String scmServerUrl)
      throws ScmUnauthorizedException, ScmCommunicationException, UnknownScmProviderException {
    OAuthToken oAuthToken;

    if (githubApiClient == null || !githubApiClient.isConnected(scmServerUrl)) {
      LOG.debug("not a  valid url {} for current fetcher ", scmServerUrl);
      return null;
    }
    try {
      oAuthToken = oAuthAPI.getOrRefreshToken(providerName);
      String tokenName = NameGenerator.generate(OAUTH_2_PREFIX, 5);
      String tokenId = NameGenerator.generate("id-", 5);
      Optional<Pair<Boolean, String>> valid =
          isValid(
              new PersonalAccessTokenParams(
                  scmServerUrl,
                  // Despite the fact that we may have two GitHub oauth providers, we always set
                  // "github" to the token provider name. The specific GitHub oauth provider
                  // references to the specific token by the url parameter.
                  OAUTH_PROVIDER_NAME,
                  tokenName,
                  tokenId,
                  oAuthToken.getToken(),
                  null));
      if (valid.isEmpty()) {
        throw buildScmUnauthorizedException(cheSubject);
      } else if (!valid.get().first) {
        throw new ScmCommunicationException(
            "Current token doesn't have the necessary privileges. Please make sure Che app scopes are correct and containing at least: "
                + DEFAULT_TOKEN_SCOPES.toString());
      }
      return new PersonalAccessToken(
          scmServerUrl,
          OAUTH_PROVIDER_NAME,
          cheSubject.getUserId(),
          valid.get().second,
          tokenName,
          tokenId,
          oAuthToken.getToken());
    } catch (UnauthorizedException e) {
      throw buildScmUnauthorizedException(cheSubject);
    } catch (NotFoundException nfe) {
      throw new UnknownScmProviderException(nfe.getMessage(), scmServerUrl);
    } catch (ServerException | ForbiddenException | BadRequestException | ConflictException e) {
      LOG.error(e.getMessage());
      throw new ScmCommunicationException(e.getMessage(), e);
    }
  }

  private ScmUnauthorizedException buildScmUnauthorizedException(Subject cheSubject) {
    return new ScmUnauthorizedException(
        cheSubject.getUserName()
            + " is not authorized in "
            + this.providerName
            + " OAuth provider.",
        this.providerName,
        "2.0",
        getLocalAuthenticateUrl());
  }

  @Override
  @Deprecated
  public Optional<Boolean> isValid(PersonalAccessToken personalAccessToken) {
    if (!githubApiClient.isConnected(personalAccessToken.getScmProviderUrl())) {
      LOG.debug("not a valid url {} for current fetcher ", personalAccessToken.getScmProviderUrl());
      return Optional.empty();
    }

    try {
      if (personalAccessToken.getScmTokenName() != null
          && personalAccessToken.getScmTokenName().startsWith(OAUTH_2_PREFIX)) {
        String[] scopes = githubApiClient.getTokenScopes(personalAccessToken.getToken()).second;
        return Optional.of(containsScopes(scopes, DEFAULT_TOKEN_SCOPES));
      } else {
        // No REST API for PAT-s in Github found yet. Just try to do some action.
        GithubUser user = githubApiClient.getUser(personalAccessToken.getToken());
        if (personalAccessToken.getScmUserName().equals(user.getLogin())) {
          return Optional.of(Boolean.TRUE);
        } else {
          return Optional.of(Boolean.FALSE);
        }
      }
    } catch (ScmItemNotFoundException
        | ScmCommunicationException
        | ScmBadRequestException
        | ScmUnauthorizedException e) {
      return Optional.of(Boolean.FALSE);
    }
  }

  @Override
  public Optional<Pair<Boolean, String>> isValid(PersonalAccessTokenParams params)
      throws ScmCommunicationException {
    GithubApiClient apiClient;
    if (githubApiClient.isConnected(params.getScmProviderUrl())) {
      // The url from the token has the same url as the api client, no need to create a new one.
      apiClient = githubApiClient;
    } else {
      if (OAUTH_PROVIDER_NAME.equals(params.getScmTokenName())) {
        apiClient = new GithubApiClient(params.getScmProviderUrl());
      } else {
        LOG.debug("not a  valid url {} for current fetcher ", params.getScmProviderUrl());
        return Optional.empty();
      }
    }
    try {
      if (params.getScmTokenName() != null && params.getScmTokenName().startsWith(OAUTH_2_PREFIX)) {
        Pair<String, String[]> pair = apiClient.getTokenScopes(params.getToken());
        return Optional.of(
            Pair.of(
                containsScopes(pair.second, DEFAULT_TOKEN_SCOPES) ? Boolean.TRUE : Boolean.FALSE,
                pair.first));
      } else {
        // TODO: add PAT scope validation
        // No REST API for PAT-s in Github found yet. Just try to do some action.
        GithubUser user = apiClient.getUser(params.getToken());
        return Optional.of(Pair.of(Boolean.TRUE, user.getLogin()));
      }
    } catch (ScmItemNotFoundException | ScmBadRequestException | ScmUnauthorizedException e) {
      return Optional.empty();
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
        + providerName
        + "&scope="
        + Joiner.on(',').join(DEFAULT_TOKEN_SCOPES)
        + "&request_method=POST&signature_method=rsa";
  }
}
