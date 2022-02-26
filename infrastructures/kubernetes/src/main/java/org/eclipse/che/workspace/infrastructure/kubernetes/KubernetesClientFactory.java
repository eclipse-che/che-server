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
package org.eclipse.che.workspace.infrastructure.kubernetes;

import io.fabric8.kubernetes.client.BaseKubernetesClient;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.http.HttpClient;
import io.fabric8.kubernetes.client.utils.HttpClientUtils;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.OkHttpClient;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.annotation.Nullable;

/**
 * @author Sergii Leshchenko
 * @author Anton Korneta
 */
@Singleton
public class KubernetesClientFactory {

  /** {@link HttpClient} instance shared by all Kubernetes clients. */
  protected final HttpClient httpClient;

  protected final KubernetesClientConfigFactory configBuilder;

  @Inject
  public KubernetesClientFactory(KubernetesClientConfigFactory configBuilder) {
    this.httpClient = HttpClientUtils.createHttpClient(configBuilder.buildDefaultConfig());
    this.configBuilder = configBuilder;
  }

  /**
   * Creates an instance of {@link KubernetesClient} that can be used to perform any operation
   * related to a given workspace. </br> For all operations performed in the context of a given
   * workspace (workspace start, workspace stop, etc ...), this method should be used to retrieve a
   * Kubernetes client.
   *
   * @param workspaceId Identifier of the workspace on which Kubernetes operations will be performed
   * @throws InfrastructureException if any error occurs on client instance creation.
   */
  public KubernetesClient create(String workspaceId) throws InfrastructureException {
    Config configForWorkspace = buildConfig(getDefaultConfig(), workspaceId);

    return create(configForWorkspace);
  }

  /**
   * Creates an instance of {@link KubernetesClient} that can be used to perform any operation
   * <strong>that is not related to a given workspace</strong>. </br> For all operations performed
   * in the context of a given workspace (workspace start, workspace stop, etc ...), the {@code
   * create(String workspaceId)} method should be used to retrieve a Kubernetes client.
   *
   * @throws InfrastructureException if any error occurs on client instance creation.
   */
  public KubernetesClient create() throws InfrastructureException {
    return create(buildConfig(getDefaultConfig(), null));
  }

  /**
   * Shuts down the {@link KubernetesClient} by closing its connection pool. Typically should be
   * called on application tear down.
   */
  public void shutdownClient() {
    httpClient.close();
  }

  /**
   * Retrieves the default Kubernetes {@link Config} that will be the base configuration to create
   * per-workspace configurations.
   */
  public Config getDefaultConfig() {
    return configBuilder.buildDefaultConfig();
  }

  /**
   * Builds the Kubernetes {@link Config} object based on a provided {@link Config} object and an
   * optional workspace ID.
   */
  protected Config buildConfig(Config config, @Nullable String workspaceId)
      throws InfrastructureException {
    return configBuilder.buildConfig(config, workspaceId);
  }

  /**
   * Creates instance of {@link KubernetesClient} that uses an {@link OkHttpClient} instance derived
   * from the shared {@code httpClient} instance in which interceptors are overridden to
   * authenticate with the credentials (user/password or Oauth token) contained in the {@code
   * config} parameter.
   */
  protected BaseKubernetesClient<?> create(Config config) {
    return new UnclosableKubernetesClient(httpClient, config);
  }

  /**
   * Decorates the {@link DefaultKubernetesClient} so that it can not be closed from the outside.
   */
  private static class UnclosableKubernetesClient extends DefaultKubernetesClient {

    public UnclosableKubernetesClient(HttpClient httpClient, Config config) {
      super(httpClient, config);
    }

    @Override
    public void close() {}
  }
}
