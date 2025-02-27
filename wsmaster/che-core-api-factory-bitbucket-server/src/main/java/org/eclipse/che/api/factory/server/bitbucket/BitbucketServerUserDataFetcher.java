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
package org.eclipse.che.api.factory.server.bitbucket;

import static java.util.stream.Collectors.toList;

import com.google.common.base.Splitter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.api.factory.server.bitbucket.server.BitbucketServerApiClient;
import org.eclipse.che.api.factory.server.bitbucket.server.BitbucketUser;
import org.eclipse.che.api.factory.server.scm.GitUserData;
import org.eclipse.che.api.factory.server.scm.GitUserDataFetcher;
import org.eclipse.che.api.factory.server.scm.PersonalAccessToken;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmConfigurationPersistenceException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.lang.StringUtils;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.security.oauth.OAuthAPI;
import org.eclipse.che.security.oauth1.NoopOAuthAuthenticator;

/** Bitbucket git user data retriever. */
public class BitbucketServerUserDataFetcher implements GitUserDataFetcher {

  private final String OAUTH_PROVIDER_NAME = "bitbucket-server";
  private final String apiEndpoint;

  /** Bitbucket API client. */
  private final BitbucketServerApiClient bitbucketServerApiClient;

  private final PersonalAccessTokenManager personalAccessTokenManager;
  private final OAuthAPI oAuthAPI;

  private final List<String> registeredBitbucketEndpoints;

  @Inject
  public BitbucketServerUserDataFetcher(
      @Named("che.api") String apiEndpoint,
      @Nullable @Named("che.integration.bitbucket.server_endpoints") String bitbucketEndpoints,
      BitbucketServerApiClient bitbucketServerApiClient,
      OAuthAPI oAuthAPI,
      PersonalAccessTokenManager personalAccessTokenManager) {
    this.oAuthAPI = oAuthAPI;
    this.apiEndpoint = apiEndpoint;
    this.bitbucketServerApiClient = bitbucketServerApiClient;
    this.personalAccessTokenManager = personalAccessTokenManager;
    if (bitbucketEndpoints != null) {
      this.registeredBitbucketEndpoints =
          Splitter.on(",")
              .splitToStream(bitbucketEndpoints)
              .map(e -> StringUtils.trimEnd(e, '/'))
              .collect(toList());
    } else {
      this.registeredBitbucketEndpoints = Collections.emptyList();
    }
  }

  @Override
  public GitUserData fetchGitUserData(String namespaceName)
      throws ScmUnauthorizedException, ScmCommunicationException,
          ScmConfigurationPersistenceException, ScmItemNotFoundException {
    Subject cheSubject = EnvironmentContext.getCurrent().getSubject();
    for (String bitbucketServerEndpoint : this.registeredBitbucketEndpoints) {
      if (bitbucketServerApiClient.isConnected(bitbucketServerEndpoint)) {
        try {
          BitbucketUser user = bitbucketServerApiClient.getUser();
          return new GitUserData(user.getDisplayName(), user.getEmailAddress());
        } catch (ScmItemNotFoundException e) {
          throw new ScmCommunicationException(e.getMessage(), e);
        }
      }
    }

    // Try go get user data using personal access token
    Optional<PersonalAccessToken> personalAccessToken =
        this.personalAccessTokenManager.get(cheSubject, OAUTH_PROVIDER_NAME, null, namespaceName);
    if (personalAccessToken.isPresent()) {
      PersonalAccessToken token = personalAccessToken.get();
      HttpBitbucketServerApiClient httpBitbucketServerApiClient =
          new HttpBitbucketServerApiClient(
              StringUtils.trimEnd(token.getScmProviderUrl(), '/'),
              new NoopOAuthAuthenticator(),
              oAuthAPI,
              this.apiEndpoint);

      BitbucketUser user = httpBitbucketServerApiClient.getUser(token.getToken());
      return new GitUserData(user.getDisplayName(), user.getEmailAddress());
    }

    throw new ScmCommunicationException("Failed to retrieve git user data from Bitbucket");
  }
}
