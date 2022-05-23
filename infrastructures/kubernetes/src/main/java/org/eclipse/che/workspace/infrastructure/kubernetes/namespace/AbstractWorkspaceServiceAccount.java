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
package org.eclipse.che.workspace.infrastructure.kubernetes.namespace;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.eclipse.che.api.user.server.PreferenceManager;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.util.KubernetesSharedPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements the logic for creating the roles and role bindings for the workspace
 * service account. Because of the differences between Kubernetes and OpenShift we need to use a lot
 * of generic params.
 *
 * @param <Client> the type of the client to use
 * @param <R> the Role type
 * @param <B> the RoleBinding type
 */
public abstract class AbstractWorkspaceServiceAccount<
    Client extends KubernetesClient, R extends HasMetadata, B extends HasMetadata> {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractWorkspaceServiceAccount.class);
  public static final String EXEC_ROLE_NAME = "exec";
  public static final String VIEW_ROLE_NAME = "workspace-view";
  public static final String METRICS_ROLE_NAME = "workspace-metrics";
  public static final String SECRETS_ROLE_NAME = "workspace-secrets";
  public static final String CONFIGMAPS_ROLE_NAME = "workspace-configmaps";
  public static final String CREDENTIALS_SECRET_NAME = "workspace-credentials-secret";
  public static final String PREFERENCES_CONFIGMAP_NAME = "workspace-preferences-configmap";
  public static final String GIT_USERDATA_CONFIGMAP_NAME = "workspace-userdata-gitconfig-configmap";

  protected final String namespace;
  protected final String serviceAccountName;
  private final ClientFactory<Client> clientFactory;
  private final String workspaceId;
  private final Set<String> clusterRoleNames;
  private final Function<
          Client, MixedOperation<R, ? extends KubernetesResourceList<R>, ? extends Resource<R>>>
      roles;
  private final Function<
          Client, MixedOperation<B, ? extends KubernetesResourceList<B>, ? extends Resource<B>>>
      roleBindings;

  protected AbstractWorkspaceServiceAccount(
      String workspaceId,
      String namespace,
      String serviceAccountName,
      Set<String> clusterRoleNames,
      ClientFactory<Client> clientFactory,
      Function<
              Client, MixedOperation<R, ? extends KubernetesResourceList<R>, ? extends Resource<R>>>
          roles,
      Function<
              Client, MixedOperation<B, ? extends KubernetesResourceList<B>, ? extends Resource<B>>>
          roleBindings) {
    this.workspaceId = workspaceId;
    this.namespace = namespace;
    this.serviceAccountName = serviceAccountName;
    this.clusterRoleNames = clusterRoleNames;
    this.clientFactory = clientFactory;
    this.roles = roles;
    this.roleBindings = roleBindings;
  }

  /**
   * Make sure that workspace service account exists and has `view` and `exec` role bindings, as
   * well as create workspace-view and exec roles in namespace scope
   *
   * @throws InfrastructureException when any exception occurred
   */
  public void prepare() throws InfrastructureException {
    Client k8sClient = clientFactory.create(workspaceId);
    if (k8sClient.serviceAccounts().inNamespace(namespace).withName(serviceAccountName).get()
        == null) {
      createWorkspaceServiceAccount(k8sClient);
    }
    ensureImplicitRolesWithBindings(k8sClient);
    ensureExplicitClusterRoleBindings(k8sClient);
  }

  /**
   * Creates implicit Roles and RoleBindings for workspace ServiceAccount that we need to have fully
   * working workspaces with this SA.
   *
   * <p>creates {@code <sa>-exec} and {@code <sa>-view}
   */
  private void ensureImplicitRolesWithBindings(Client k8sClient) {
    // exec role
    ensureRoleWithBinding(
        k8sClient,
        buildRole(
            EXEC_ROLE_NAME,
            singletonList("pods/exec"),
            emptyList(),
            singletonList(""),
            singletonList("create")),
        serviceAccountName + "-exec");

    // view role
    ensureRoleWithBinding(
        k8sClient,
        buildRole(
            VIEW_ROLE_NAME,
            Arrays.asList("pods", "services"),
            emptyList(),
            singletonList(""),
            singletonList("list")),
        serviceAccountName + "-view");

    // metrics role
    try {
      if (k8sClient.supportsApiPath("/apis/metrics.k8s.io")) {
        ensureRoleWithBinding(
            k8sClient,
            buildRole(
                METRICS_ROLE_NAME,
                Arrays.asList("pods", "nodes"),
                emptyList(),
                singletonList("metrics.k8s.io"),
                Arrays.asList("list", "get", "watch")),
            serviceAccountName + "-metrics");
      }
    } catch (KubernetesClientException e) {
      // workaround to unblock workspace start if no permissions for metrics
      if (e.getCode() == 403) {
        LOG.warn(
            "Unable to add metrics roles due to insufficient permissions. Workspace metrics will be disabled.");
      } else {
        throw e;
      }
    }

    // credentials-secret role
    ensureRoleWithBinding(
        k8sClient,
        buildRole(
            SECRETS_ROLE_NAME,
            singletonList("secrets"),
            singletonList(CREDENTIALS_SECRET_NAME),
            singletonList(""),
            Arrays.asList("get", "patch")),
        serviceAccountName + "-secrets");

    // preferences-configmap role
    ensureRoleWithBinding(
        k8sClient,
        buildRole(
            CONFIGMAPS_ROLE_NAME,
            singletonList("configmaps"),
            singletonList(PREFERENCES_CONFIGMAP_NAME),
            singletonList(""),
            Arrays.asList("get", "patch")),
        serviceAccountName + "-configmaps");
  }

  private void ensureRoleWithBinding(Client k8sClient, R role, String bindingName) {
    ensureRole(k8sClient, role);
    //noinspection unchecked
    roleBindings
        .apply(k8sClient)
        .inNamespace(namespace)
        .createOrReplace(createRoleBinding(role.getMetadata().getName(), bindingName, false));
  }

  /**
   * Creates workspace ServiceAccount ClusterRoleBindings that are defined in
   * 'che.infra.kubernetes.workspace_sa_cluster_roles' property.
   *
   * @see KubernetesNamespaceFactory#KubernetesNamespaceFactory(String, String, String, boolean,
   *     boolean, String, String, KubernetesClientFactory, CheServerKubernetesClientFactory,
   *     UserManager, PreferenceManager, KubernetesSharedPool)
   */
  private void ensureExplicitClusterRoleBindings(Client k8sClient) {
    // If the user specified an additional cluster roles for the workspace,
    // create a role binding for them too
    int idx = 0;
    for (String clusterRoleName : this.clusterRoleNames) {
      if (k8sClient.rbac().clusterRoles().withName(clusterRoleName).get() != null) {
        //noinspection unchecked
        roleBindings
            .apply(k8sClient)
            .inNamespace(namespace)
            .createOrReplace(
                createRoleBinding(clusterRoleName, serviceAccountName + "-cluster" + idx++, true));
      } else {
        LOG.warn(
            "Unable to find the cluster role {}. Skip creating custom role binding.",
            clusterRoleName);
      }
    }
  }

  /**
   * Builds a new role in the configured namespace but does not persist it.
   *
   * @param name the name of the role
   * @param resources the resources the role grants access to
   * @param resourceNames specific resource names witch the role grants access to.
   * @param verbs the verbs the role allows
   * @return the role object for the given type of Client
   */
  protected abstract R buildRole(
      String name,
      List<String> resources,
      List<String> resourceNames,
      List<String> apiGroups,
      List<String> verbs);

  /**
   * Builds a new role binding but does not persist it.
   *
   * @param roleName the name of the role to bind to
   * @param bindingName the name of the binding
   * @param clusterRole whether the binding is for a cluster role or to a role in the namespace
   * @return
   */
  protected abstract B createRoleBinding(String roleName, String bindingName, boolean clusterRole);

  private void createWorkspaceServiceAccount(Client k8sClient) {
    k8sClient
        .serviceAccounts()
        .inNamespace(namespace)
        .createOrReplace(
            new ServiceAccountBuilder()
                .withAutomountServiceAccountToken(true)
                .withNewMetadata()
                .withName(serviceAccountName)
                .endMetadata()
                .build());
  }

  private void ensureRole(Client k8sClient, R role) {
    //noinspection unchecked
    roles.apply(k8sClient).inNamespace(namespace).createOrReplace(role);
  }

  public interface ClientFactory<C extends KubernetesClient> {

    C create(String workspaceId) throws InfrastructureException;
  }
}
