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
package org.eclipse.che.api.factory.server.github;

import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.security.oauth.OAuthAPI;

/** GitHub OAuth token retriever. */
public class GithubPersonalAccessTokenFetcher extends AbstractGithubPersonalAccessTokenFetcher {

  /** Name of this OAuth provider as found in OAuthAPI. */
  private static final String OAUTH_PROVIDER_NAME = "github";

  @Inject
  public GithubPersonalAccessTokenFetcher(
      @Named("che.api") String apiEndpoint,
      @Nullable @Named("che.integration.github.oauth_endpoint") String oauthEndpoint,
      OAuthAPI oAuthAPI) {
    super(apiEndpoint, oAuthAPI, new GithubApiClient(oauthEndpoint), OAUTH_PROVIDER_NAME);
  }

  GithubPersonalAccessTokenFetcher(
      @Named("che.api") String apiEndpoint, OAuthAPI oAuthAPI, GithubApiClient githubApiClient) {
    super(apiEndpoint, oAuthAPI, githubApiClient, OAUTH_PROVIDER_NAME);
  }
}
