/*
 * Copyright (c) 2012-2022 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.workspace.infrastructure.kubernetes.multiuser.oauth;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class creates {@link Config} from Kubernetes OIDC token. It does not guarantee that token is
 * valid.
 */
@Singleton
public class KubernetesOidcProviderConfigFactory extends KubernetesClientConfigFactory {
  private static final Logger LOG =
      LoggerFactory.getLogger(KubernetesOidcProviderConfigFactory.class);

  @Inject
  public KubernetesOidcProviderConfigFactory(
      @Nullable @Named("che.infra.kubernetes.master_url") String masterUrl,
      @Nullable @Named("che.infra.kubernetes.trust_certs") Boolean doTrustCerts) {
    super(masterUrl, doTrustCerts);
  }

  @Override
  public boolean isPersonalized() {
    return getToken().isPresent();
  }

  /**
   * Builds the OpenShift {@link Config} object based on a default {@link Config} object and token
   * stored in {@link EnvironmentContext}. It ignores 'workspaceId'.
   */
  public Config buildConfig(Config defaultConfig, @Nullable String workspaceId) {
    return getToken()
        .map((token) -> new ConfigBuilder(defaultConfig).withOauthToken(token).build())
        .orElseGet(
            () -> {
              LOG.debug("NO TOKEN FOUND. Getting default client config.");
              return defaultConfig;
            });
  }

  private Optional<String> getToken() {
    return Optional.ofNullable(EnvironmentContext.getCurrent().getSubject().getToken());
  }
}
