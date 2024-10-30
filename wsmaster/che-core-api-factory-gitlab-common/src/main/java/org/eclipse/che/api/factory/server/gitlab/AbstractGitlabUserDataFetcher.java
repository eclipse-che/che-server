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
package org.eclipse.che.api.factory.server.gitlab;

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.eclipse.che.api.factory.server.scm.*;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.commons.annotation.Nullable;

/** Gitlab OAuth token retriever. */
public class AbstractGitlabUserDataFetcher extends AbstractGitUserDataFetcher {

  private final String serverUrl;
  private final String apiEndpoint;
  private final String providerName;

  public static final Set<String> DEFAULT_TOKEN_SCOPES =
      ImmutableSet.of("api", "write_repository", "openid");
  private static final String GITLAB_SAAS_ENDPOINT = "https://gitlab.com";

  public AbstractGitlabUserDataFetcher(
      @Nullable String serverUrl,
      String apiEndpoint,
      PersonalAccessTokenManager personalAccessTokenManager,
      String providerName) {
    super(providerName, serverUrl, personalAccessTokenManager);
    this.serverUrl = isNullOrEmpty(serverUrl) ? GITLAB_SAAS_ENDPOINT : serverUrl;
    this.apiEndpoint = apiEndpoint;
    this.providerName = providerName;
  }

  @Override
  protected GitUserData fetchGitUserDataWithOAuthToken(String token)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException,
          ScmUnauthorizedException {
    GitlabUser user = new GitlabApiClient(serverUrl).getUser(token);
    return new GitUserData(user.getName(), user.getEmail());
  }

  @Override
  protected GitUserData fetchGitUserDataWithPersonalAccessToken(
      PersonalAccessToken personalAccessToken)
      throws ScmItemNotFoundException, ScmCommunicationException, ScmBadRequestException,
          ScmUnauthorizedException {
    GitlabUser user =
        new GitlabApiClient(personalAccessToken.getScmProviderUrl())
            .getUser(personalAccessToken.getToken());
    return new GitUserData(user.getName(), user.getEmail());
  }

  protected String getLocalAuthenticateUrl() {
    return apiEndpoint
        + "/oauth/authenticate?oauth_provider="
        + providerName
        + "&scope="
        + Joiner.on('+').join(DEFAULT_TOKEN_SCOPES)
        + "&request_method=POST&signature_method=rsa";
  }
}
