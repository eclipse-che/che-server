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

import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.commons.annotation.Nullable;

/** GitHub user data retriever. */
public class GithubUserDataFetcher extends AbstractGithubUserDataFetcher {
  /** Name of this OAuth provider as found in OAuthAPI. */
  private static final String OAUTH_PROVIDER_NAME = "github";

  @Inject
  public GithubUserDataFetcher(
      @Named("che.api") String apiEndpoint,
      @Nullable @Named("che.integration.github.oauth_endpoint") String oauthEndpoint,
      PersonalAccessTokenManager personalAccessTokenManager) {
    super(
        apiEndpoint,
        personalAccessTokenManager,
        new GithubApiClient(oauthEndpoint),
        OAUTH_PROVIDER_NAME);
  }

  GithubUserDataFetcher(
      String apiEndpoint,
      PersonalAccessTokenManager personalAccessTokenManager,
      GithubApiClient githubApiClient) {
    super(apiEndpoint, personalAccessTokenManager, githubApiClient, OAUTH_PROVIDER_NAME);
  }
}
