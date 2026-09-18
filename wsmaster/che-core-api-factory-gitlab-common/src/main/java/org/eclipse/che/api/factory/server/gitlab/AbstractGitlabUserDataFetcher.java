/*
 * Copyright (c) 2012-2026 Red Hat, Inc.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.eclipse.che.api.factory.server.scm.*;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.lang.UrlTargetValidator;

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
    super(
        providerName,
        isNullOrEmpty(serverUrl) ? GITLAB_SAAS_ENDPOINT : serverUrl,
        personalAccessTokenManager);
    this.serverUrl = super.oAuthProviderUrl;
    this.apiEndpoint = apiEndpoint;
    this.providerName = providerName;
  }

  @Override
  protected GitUserData fetchGitUserDataWithOAuthToken(String token)
      throws ScmItemNotFoundException,
          ScmCommunicationException,
          ScmBadRequestException,
          ScmUnauthorizedException {
    GitlabUser user = new GitlabApiClient(serverUrl).getUser(token);
    return new GitUserData(user.getName(), user.getEmail());
  }

  /**
   * Tells whether the server may contact an SCM server that is not the configured provider
   * endpoint. Such a URL comes from a secret in the user's namespace, so contacting it
   * unconditionally would let anyone holding a namespace have the server reach services only it can
   * see (SSRF). A provider on a private network is reached through the configured provider
   * endpoint, which is matched before it comes to this.
   */
  @VisibleForTesting
  boolean canContact(String scmServerUrl) {
    return serverUrl.equals(scmServerUrl) || UrlTargetValidator.isAllowed(scmServerUrl);
  }

  @Override
  protected GitUserData fetchGitUserDataWithPersonalAccessToken(
      PersonalAccessToken personalAccessToken)
      throws ScmItemNotFoundException,
          ScmCommunicationException,
          ScmBadRequestException,
          ScmUnauthorizedException {
    if (!canContact(personalAccessToken.getScmProviderUrl())) {
      throw new ScmCommunicationException(
          "Refusing to contact "
              + personalAccessToken.getScmProviderUrl()
              + ": it is not the configured GitLab endpoint and does not point to a publicly"
              + " routable host.");
    }
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
