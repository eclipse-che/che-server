/*
 * Copyright (c) 2012-2021 Red Hat, Inc.
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

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.security.oauth.shared.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class GitHubOAuthAuthenticatorProvider implements Provider<OAuthAuthenticator> {
  private static final Logger LOG = LoggerFactory.getLogger(GitHubOAuthAuthenticatorProvider.class);
  public static final String GITHUB_CLIENT_ID_PATH = "/che-conf/oauth/github/id";
  public static final String GITHUB_CLIENT_SECRET_PATH = "/che-conf/oauth/github/secret";

  private final OAuthAuthenticator authenticator;

  @Inject
  public GitHubOAuthAuthenticatorProvider(
      @Nullable @Named("che.oauth.github.redirecturis") String[] redirectUris,
      @Nullable @Named("che.oauth.github.authuri") String authUri,
      @Nullable @Named("che.oauth.github.tokenuri") String tokenUri)
      throws IOException {
    authenticator =
        getOAuthAuthenticator(
            redirectUris, authUri, tokenUri, GITHUB_CLIENT_ID_PATH, GITHUB_CLIENT_SECRET_PATH);
  }

  @Override
  public OAuthAuthenticator get() {
    return authenticator;
  }

  @VisibleForTesting
  OAuthAuthenticator getOAuthAuthenticator(
      String[] redirectUris,
      String authUri,
      String tokenUri,
      String clientIdPath,
      String clientSecretPath)
      throws IOException {

    if (isNullOrEmpty(authUri)
        || isNullOrEmpty(tokenUri)
        || Objects.isNull(redirectUris)
        || redirectUris.length == 0) {
      LOG.debug(
          "URIs for GitHub OAuth authentication are missing or empty. Make sure your configuration is correct.");
      return new NoopOAuthAuthenticator();
    }

    String clientId, clientSecret;
    try {
      clientId = Files.readString(Path.of(clientIdPath));
      clientSecret = Files.readString(Path.of(clientSecretPath));
    } catch (IOException e) {
      LOG.debug(
          "NoopOAuthAuthenticator will be used, because files containing GitHub credentials cannot be accessed. Cause: {}",
          e.getMessage());
      return new NoopOAuthAuthenticator();
    }

    if (isNullOrEmpty(clientId) || isNullOrEmpty(clientSecret)) {
      LOG.debug(
          "A file containing GitHub credentials is empty. NoopOAuthAuthenticator will be used.");
      return new NoopOAuthAuthenticator();
    }

    return new GitHubOAuthAuthenticator(clientId, clientSecret, redirectUris, authUri, tokenUri);
  }

  static class NoopOAuthAuthenticator extends OAuthAuthenticator {
    @Override
    public User getUser(OAuthToken accessToken) throws OAuthAuthenticationException {
      throw new OAuthAuthenticationException(
          "The fallback noop authenticator cannot be used for GitHub authentication. Make sure OAuth is properly configured.");
    }

    @Override
    public String getOAuthProvider() {
      return "Noop";
    }
  }
}
