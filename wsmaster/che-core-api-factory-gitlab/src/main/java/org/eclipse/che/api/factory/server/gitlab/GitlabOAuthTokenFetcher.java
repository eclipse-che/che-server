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

import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.security.oauth.OAuthAPI;

/** GitLab OAuth token retriever. */
public class GitlabOAuthTokenFetcher extends AbstractGitlabOAuthTokenFetcher {

  private static final String OAUTH_PROVIDER_NAME = "gitlab";

  @Inject
  public GitlabOAuthTokenFetcher(
      @Nullable @Named("che.integration.gitlab.oauth_endpoint") String serverUrl,
      @Named("che.api") String apiEndpoint,
      OAuthAPI oAuthAPI) {
    super(serverUrl, apiEndpoint, oAuthAPI, OAUTH_PROVIDER_NAME);
  }
}
