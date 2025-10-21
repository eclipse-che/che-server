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
public class GitLabOAuthAuthenticatorProvider extends AbstractGitLabOAuthAuthenticatorProvider {
  private static final String PROVIDER_NAME = "gitlab";

  @Inject
  public GitLabOAuthAuthenticatorProvider(
      @Nullable @Named("che.oauth2.gitlab.clientid_filepath") String clientIdPath,
      @Nullable @Named("che.oauth2.gitlab.clientsecret_filepath") String clientSecretPath,
      @Nullable @Named("che.integration.gitlab.oauth_endpoint") String gitlabEndpoint,
      @Named("che.api") String cheApiEndpoint)
      throws IOException {
    super(clientIdPath, clientSecretPath, gitlabEndpoint, cheApiEndpoint, PROVIDER_NAME);
  }
}
