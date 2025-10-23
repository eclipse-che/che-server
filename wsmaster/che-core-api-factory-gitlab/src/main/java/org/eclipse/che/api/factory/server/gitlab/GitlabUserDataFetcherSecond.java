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
import org.eclipse.che.api.factory.server.scm.*;
import org.eclipse.che.commons.annotation.Nullable;

/** Gitlab OAuth token retriever. */
public class GitlabUserDataFetcherSecond extends AbstractGitlabUserDataFetcher {

  /** Name of this OAuth provider as found in OAuthAPI. */
  private static final String OAUTH_PROVIDER_NAME = "gitlab_2";

  @Inject
  public GitlabUserDataFetcherSecond(
      @Nullable @Named("che.integration.gitlab.oauth_endpoint_2") String serverUrl,
      @Named("che.api") String apiEndpoint,
      PersonalAccessTokenManager personalAccessTokenManager) {
    super(serverUrl, apiEndpoint, personalAccessTokenManager, OAUTH_PROVIDER_NAME);
  }
}
