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
import javax.inject.Singleton;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.DevfileFilenamesProvider;
import org.eclipse.che.commons.annotation.Nullable;

/**
 * Parser of String Github URLs and provide {@link GithubUrl} objects.
 *
 * @author Florent Benoit
 */
@Singleton
public class GithubURLParserSecond extends AbstractGithubURLParser {
  /** Name of this OAuth provider as found in OAuthAPI. */
  private static final String OAUTH_PROVIDER_NAME = "github_2";

  @Inject
  public GithubURLParserSecond(
      PersonalAccessTokenManager tokenManager,
      DevfileFilenamesProvider devfileFilenamesProvider,
      @Nullable @Named("che.integration.github.oauth_endpoint_2") String oauthEndpoint,
      @Named("che.integration.github.disable_subdomain_isolation_2")
          boolean disableSubdomainIsolation) {
    super(
        tokenManager,
        devfileFilenamesProvider,
        new GithubApiClient(oauthEndpoint),
        oauthEndpoint,
        disableSubdomainIsolation,
        OAUTH_PROVIDER_NAME);
  }
}
