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
package org.eclipse.che.workspace.infrastructure.openshift;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.http.HttpClient;
import io.fabric8.kubernetes.client.utils.HttpClientUtils;
import io.fabric8.kubernetes.client.utils.TokenRefreshInterceptor;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.OpenShiftConfig;
import io.fabric8.openshift.client.internal.OpenShiftOAuthInterceptor;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientConfigFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;

/**
 * @author Sergii Leshchenko
 * @author Anton Korneta
 */
@Singleton
public class OpenShiftClientFactory extends KubernetesClientFactory {
  @Inject
  public OpenShiftClientFactory(KubernetesClientConfigFactory configBuilder) {
    super(configBuilder);
  }

  /**
   * Creates an instance of {@link OpenShiftClient} that can be used to perform any operation
   * related to a given workspace. </br> <strong>Important note: </strong> However, in some
   * use-cases involving web sockets, the Openshift client may introduce connection leaks. That's
   * why this method should only be used for API calls that are specific to Openshift and thus not
   * available in the {@link KubernetesClient} class: mainly route-related calls and project-related
   * calls. For all other Kubernetes standard calls, prefer the {@code create(String workspaceId)}
   * method that returns a Kubernetes client.
   *
   * @param workspaceId Identifier of the workspace on which Openshift operations will be performed
   * @throws InfrastructureException if any error occurs on client instance creation.
   */
  public OpenShiftClient createOC(String workspaceId) throws InfrastructureException {
    Config configForWorkspace = buildConfig(getDefaultConfig(), workspaceId);
    return create(configForWorkspace);
  }

  /**
   * Creates an instance of {@link OpenShiftClient} that can be used to perform any operation
   * <strong>that is not related to a given workspace</strong>. </br> For operations performed in
   * the context of a given workspace (workspace start, workspace stop, etc ...), the {@code
   * createOC(String workspaceId)} method should be used to retrieve an Openshift client. </br>
   * <strong>Important note: </strong> However in some use-cases involving web sockets, the
   * Openshift client may introduce connection leaks. That's why this method should only be used for
   * API calls that are specific to Openshift and thus not available in the {@link KubernetesClient}
   * class: mainly route-related calls and project-related calls. For all other Kubernetes standard
   * calls, just use the {@code create()} or {@code create(String workspaceId)} methods that return
   * a Kubernetes client.
   *
   * @throws InfrastructureException if any error occurs on client instance creation.
   */
  public OpenShiftClient createOC() throws InfrastructureException {
    return create(buildConfig(getDefaultConfig(), null));
  }

  public OpenShiftClient createAuthenticatedClient(String token) {
    Config config = getDefaultConfig();
    config.setOauthToken(token);
    return create(config);
  }

  protected DefaultOpenShiftClient create(Config config) {
    OpenShiftConfig openshiftConfig = new OpenShiftConfig(config);
    return new UnclosableOpenShiftClient(clientForConfig(openshiftConfig), openshiftConfig);
  }

  private HttpClient clientForConfig(OpenShiftConfig config) {
    HttpClient.Builder builder = httpClient.newBuilder().authenticatorNone();
    builder.addOrReplaceInterceptor(HttpClientUtils.HEADER_INTERCEPTOR, null);
    return builder
        .addOrReplaceInterceptor(
            TokenRefreshInterceptor.NAME, new OpenShiftOAuthInterceptor(httpClient, config))
        .build();
  }

  /**
   * Decorates the {@link DefaultKubernetesClient} so that it can not be closed from the outside.
   */
  private static class UnclosableOpenShiftClient extends DefaultOpenShiftClient {

    public UnclosableOpenShiftClient(HttpClient httpClient, OpenShiftConfig config) {
      super(httpClient, config);
    }

    @Override
    public void close() {}
  }
}
