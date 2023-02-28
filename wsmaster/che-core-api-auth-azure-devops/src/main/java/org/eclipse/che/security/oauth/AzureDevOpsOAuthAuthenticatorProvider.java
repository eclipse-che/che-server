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

import static com.google.common.base.Strings.isNullOrEmpty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.security.oauth.shared.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Azure DevOps Service Authenticator provider.
 *
 * @author Anatolii Bazko
 */
@Singleton
public class AzureDevOpsOAuthAuthenticatorProvider implements Provider<OAuthAuthenticator> {
  private static final Logger LOG =
      LoggerFactory.getLogger(AzureDevOpsOAuthAuthenticatorProvider.class);
  private final OAuthAuthenticator authenticator;

  @Inject
  public AzureDevOpsOAuthAuthenticatorProvider(
      @Named("che.api") String cheApiEndpoint,
      @Nullable @Named("che.oauth2.azure.devops.clientid_filepath") String azureDevOpsClientIdPath,
      @Nullable @Named("che.oauth2.azure.devops.clientsecret_filepath")
          String azureDevOpsClientSecretPath,
      @Named("che.integration.azure.devops.api_endpoint") String azureDevOpsApiEndpoint,
      @Named("che.integration.azure.devops.scm.api_endpoint") String azureDevOpsScmApiEndpoint,
      @Named("che.oauth.azure.devops.authuri") String authUri,
      @Named("che.oauth.azure.devops.tokenuri") String tokenUri,
      @Named("che.oauth.azure.devops.redirecturis") String[] redirectUris)
      throws IOException {
    authenticator =
        getOAuthAuthenticator(
            cheApiEndpoint,
            azureDevOpsClientIdPath,
            azureDevOpsClientSecretPath,
            azureDevOpsApiEndpoint,
            azureDevOpsScmApiEndpoint,
            authUri,
            tokenUri,
            redirectUris);
    LOG.debug("{} Azure DevOps OAuth Authenticator is used.", authenticator);
  }

  @Override
  public OAuthAuthenticator get() {
    return authenticator;
  }

  private OAuthAuthenticator getOAuthAuthenticator(
      String cheApiEndpoint,
      String clientIdPath,
      String clientSecretPath,
      String azureDevOpsApiEndpoint,
      String azureDevOpsScmApiEndpoint,
      String authUri,
      String tokenUri,
      String[] redirectUris)
      throws IOException {

    if (!isNullOrEmpty(clientIdPath) && !isNullOrEmpty(clientSecretPath)) {
      final String clientId = Files.readString(Path.of(clientIdPath)).trim();
      final String clientSecret = Files.readString(Path.of(clientSecretPath)).trim();
      if (!isNullOrEmpty(clientId) && !isNullOrEmpty(clientSecret)) {
        return new AzureDevOpsOAuthAuthenticator(
            cheApiEndpoint,
            clientId,
            clientSecret,
            azureDevOpsApiEndpoint,
            azureDevOpsScmApiEndpoint,
            authUri,
            tokenUri,
            redirectUris);
      }
    }
    return new NoopOAuthAuthenticator();
  }

  static class NoopOAuthAuthenticator extends OAuthAuthenticator {
    @Override
    public User getUser(OAuthToken accessToken) throws OAuthAuthenticationException {
      throw new OAuthAuthenticationException(
          "The fallback noop authenticator cannot be used for GitLab authentication. Make sure OAuth is properly configured.");
    }

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
