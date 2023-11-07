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
package org.eclipse.che.security.oauth;

import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.commons.annotation.Nullable;

/**
 * Provides implementation of GitHub {@link OAuthAuthenticator} based on available configuration.
 *
 * @author Anatolii Bazko
 */
@Singleton
public class GitHubOAuthAuthenticatorProviderSecond
    extends AbstractGitHubOAuthAuthenticatorProvider {
  private static final String PROVIDER_NAME = "github_2";

  @Inject
  public GitHubOAuthAuthenticatorProviderSecond(
      @Nullable @Named("che.oauth2.github.clientid_filepath_2") String gitHubClientIdPath,
      @Nullable @Named("che.oauth2.github.clientsecret_filepath_2") String gitHubClientSecretPath,
      @Nullable @Named("che.oauth.github.redirecturis") String[] redirectUris,
      @Nullable @Named("che.integration.github.oauth_endpoint_2") String oauthEndpoint,
      @Nullable @Named("che.oauth.github.authuri_2") String authUri,
      @Nullable @Named("che.oauth.github.tokenuri_2") String tokenUri)
      throws IOException {
    super(
        gitHubClientIdPath,
        gitHubClientSecretPath,
        redirectUris,
        oauthEndpoint,
        authUri,
        tokenUri,
        PROVIDER_NAME);
  }
}
