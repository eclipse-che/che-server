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

import static com.google.common.base.Strings.isNullOrEmpty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides implementation of GitLab {@link OAuthAuthenticator} based on available configuration.
 *
 * @author Pavol Baran
 */
public class AbstractGitLabOAuthAuthenticatorProvider implements Provider<OAuthAuthenticator> {
  private static final Logger LOG =
      LoggerFactory.getLogger(AbstractGitLabOAuthAuthenticatorProvider.class);
  private final OAuthAuthenticator authenticator;
  private final String providerName;

  public AbstractGitLabOAuthAuthenticatorProvider(
      String clientIdPath,
      String clientSecretPath,
      String gitlabEndpoint,
      String cheApiEndpoint,
      String providerName)
      throws IOException {
    this.providerName = providerName;
    authenticator =
        getOAuthAuthenticator(clientIdPath, clientSecretPath, gitlabEndpoint, cheApiEndpoint);
    LOG.debug("{} GitLab OAuth Authenticator is used.", authenticator);
  }

  @Override
  public OAuthAuthenticator get() {
    return authenticator;
  }

  private OAuthAuthenticator getOAuthAuthenticator(
      String clientIdPath, String clientSecretPath, String gitlabEndpoint, String cheApiEndpoint)
      throws IOException {
    if (!isNullOrEmpty(clientIdPath)
        && !isNullOrEmpty(clientSecretPath)
        && !isNullOrEmpty(gitlabEndpoint)) {
      String clientId = Files.readString(Path.of(clientIdPath));
      String clientSecret = Files.readString(Path.of(clientSecretPath));
      if (!isNullOrEmpty(clientId) && !isNullOrEmpty(clientSecret)) {
        return new GitLabOAuthAuthenticator(
            clientId, clientSecret, gitlabEndpoint, cheApiEndpoint, providerName);
      }
    }
    return new NoopOAuthAuthenticator();
  }

  static class NoopOAuthAuthenticator extends OAuthAuthenticator {

    @Override
    public String getOAuthProvider() {
      return "Noop";
    }

    @Override
    public String getEndpointUrl() {
      return "Noop";
    }
  }
}
