/*
 * Copyright (c) 2012-2026 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.factory.server;

import static com.google.common.base.Strings.isNullOrEmpty;

import java.net.URI;
import java.net.URISyntaxException;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.lang.UrlTargetValidator;

/**
 * Adds configured Git provider endpoints to the hosts that may be contacted when they are not
 * publicly routable. Without this, configuring an internal GitHub Enterprise or GitLab server still
 * leaves devfile download rejected by {@link UrlTargetValidator}.
 */
@Singleton
public class TrustedScmHostRegistrar {

  @Inject
  TrustedScmHostRegistrar(
      @Nullable @Named("che.integration.github.oauth_endpoint") String githubEndpoint,
      @Nullable @Named("che.integration.github.oauth_endpoint_2") String githubEndpoint2,
      @Nullable @Named("che.integration.gitlab.oauth_endpoint") String gitlabEndpoint,
      @Nullable @Named("che.integration.gitlab.oauth_endpoint_2") String gitlabEndpoint2,
      @Nullable @Named("che.integration.bitbucket.server_endpoints")
          String bitbucketServerEndpoints,
      @Nullable @Named("che.oauth.bitbucket.endpoint") String bitbucketEndpoint,
      @Nullable @Named("che.integration.azure.devops.scm.api_endpoint")
          String azureDevOpsEndpoint) {
    register(
        githubEndpoint,
        githubEndpoint2,
        gitlabEndpoint,
        gitlabEndpoint2,
        bitbucketServerEndpoints,
        bitbucketEndpoint,
        azureDevOpsEndpoint);
  }

  /**
   * Trusts every host in the given endpoint values. A value may be a URL, a bare hostname, a
   * comma-separated list, or unset ({@code null} or {@code NULL}).
   *
   * @param endpoints configured SCM endpoints
   */
  static void register(String... endpoints) {
    if (endpoints == null) {
      return;
    }
    for (String endpoint : endpoints) {
      if (isUnset(endpoint)) {
        continue;
      }
      for (String part : endpoint.split(",")) {
        trust(part);
      }
    }
  }

  private static void trust(String url) {
    if (isUnset(url)) {
      return;
    }
    String candidate = url.trim();
    try {
      URI uri = candidate.contains("://") ? new URI(candidate) : new URI("https://" + candidate);
      if (!isNullOrEmpty(uri.getHost())) {
        UrlTargetValidator.trustHost(uri.getHost());
      }
    } catch (URISyntaxException ignored) {
      // A malformed endpoint does not become a trusted host.
    }
  }

  private static boolean isUnset(String value) {
    return isNullOrEmpty(value) || "NULL".equalsIgnoreCase(value.trim());
  }
}
