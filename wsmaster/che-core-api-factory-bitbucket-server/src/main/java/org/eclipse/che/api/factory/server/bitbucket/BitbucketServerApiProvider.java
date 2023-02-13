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

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.base.Splitter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.eclipse.che.api.factory.server.bitbucket.server.BitbucketServerApiClient;
import org.eclipse.che.api.factory.server.bitbucket.server.NoopBitbucketServerApiClient;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.lang.StringUtils;
import org.eclipse.che.inject.ConfigurationException;
import org.eclipse.che.security.oauth.OAuthAPI;
import org.eclipse.che.security.oauth1.NoopOAuthAuthenticator;
import org.eclipse.che.security.oauth1.OAuthAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class BitbucketServerApiProvider implements Provider<BitbucketServerApiClient> {

  private static final Logger LOG = LoggerFactory.getLogger(BitbucketServerApiProvider.class);

  private final BitbucketServerApiClient bitbucketServerApiClient;
  private final String apiEndpoint;
  private final OAuthAPI oAuthAPI;

  @Inject
  public BitbucketServerApiProvider(
      @Nullable @Named("che.integration.bitbucket.server_endpoints") String bitbucketEndpoints,
      @Named("che.oauth.bitbucket.endpoint") String bitbucketOauthEndpoint,
      @Named("che.api") String apiEndpoint,
      OAuthAPI oAuthAPI,
      Set<OAuthAuthenticator> authenticators) {
    this.apiEndpoint = apiEndpoint;
    this.oAuthAPI = oAuthAPI;
    bitbucketServerApiClient = doGet(bitbucketEndpoints, bitbucketOauthEndpoint, authenticators);
    LOG.debug("Bitbucket server api is used {}", bitbucketServerApiClient);
  }

  @Override
  public BitbucketServerApiClient get() {
    return bitbucketServerApiClient;
  }

  private BitbucketServerApiClient doGet(
      String rawBitbucketEndpoints,
      String bitbucketOauthEndpoint,
      Set<OAuthAuthenticator> authenticators) {
    boolean isCloudEndpoint = bitbucketOauthEndpoint.equals("https://bitbucket.org");
    if (isCloudEndpoint && isNullOrEmpty(rawBitbucketEndpoints)) {
      return new NoopBitbucketServerApiClient();
    } else if (!isCloudEndpoint && isNullOrEmpty(rawBitbucketEndpoints)) {
      throw new ConfigurationException(
          "`che.integration.bitbucket.server_endpoints` bitbucket configuration is missing."
              + " It should contain values from 'che.oauth.bitbucket.endpoint'");
    } else if (isCloudEndpoint && !isNullOrEmpty(rawBitbucketEndpoints)) {
      return new HttpBitbucketServerApiClient(
          sanitizedEndpoints(rawBitbucketEndpoints).get(0),
          new NoopOAuthAuthenticator(),
          oAuthAPI,
          apiEndpoint);
    } else {
      bitbucketOauthEndpoint = StringUtils.trimEnd(bitbucketOauthEndpoint, '/');
      if (!sanitizedEndpoints(rawBitbucketEndpoints).contains(bitbucketOauthEndpoint)) {
        throw new ConfigurationException(
            "`che.integration.bitbucket.server_endpoints` must contain `"
                + bitbucketOauthEndpoint
                + "` value");
      } else {
        Optional<OAuthAuthenticator> authenticator = authenticators.stream().findFirst();
        if (authenticator.isEmpty()) {
          throw new ConfigurationException(
              "'che.oauth.bitbucket.endpoint' is set but BitbucketServerOAuthAuthenticator is not deployed correctly");
        }
        return new HttpBitbucketServerApiClient(
            bitbucketOauthEndpoint, authenticator.get(), oAuthAPI, apiEndpoint);
      }
    }
  }

  private static List<String> sanitizedEndpoints(String rawBitbucketEndpoints) {
    return Splitter.on(",").splitToList(rawBitbucketEndpoints).stream()
        .map(s -> StringUtils.trimEnd(s, '/'))
        .collect(Collectors.toList());
  }
}
