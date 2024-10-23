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
package org.eclipse.che.security.oauth;

import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.commons.annotation.Nullable;

/**
 * Provides implementation of GitLab {@link OAuthAuthenticator} based on available configuration.
 *
 * @author Pavol Baran
 */
@Singleton
public class GitLabOAuthAuthenticatorProviderSecond
    extends AbstractGitLabOAuthAuthenticatorProvider {
  private static final String PROVIDER_NAME = "gitlab_2";

  @Inject
  public GitLabOAuthAuthenticatorProviderSecond(
      @Nullable @Named("che.oauth2.gitlab.clientid_filepath_2") String clientIdPath,
      @Nullable @Named("che.oauth2.gitlab.clientsecret_filepath_2") String clientSecretPath,
      @Nullable @Named("che.integration.gitlab.oauth_endpoint_2") String gitlabEndpoint,
      @Named("che.api") String cheApiEndpoint)
      throws IOException {
    super(clientIdPath, clientSecretPath, gitlabEndpoint, cheApiEndpoint, PROVIDER_NAME);
  }
}
