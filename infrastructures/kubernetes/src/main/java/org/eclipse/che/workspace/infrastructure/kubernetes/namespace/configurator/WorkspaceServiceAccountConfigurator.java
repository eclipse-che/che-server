package org.eclipse.che.workspace.infrastructure.kubernetes.namespace.configurator;

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesWorkspaceServiceAccount;

public class WorkspaceServiceAccountConfigurator implements NamespaceConfigurator {

  private final KubernetesClientFactory clientFactory;

  private final String serviceAccountName;
  private final Set<String> clusterRoleNames;

  @Inject
  public WorkspaceServiceAccountConfigurator(
      @Nullable @Named("che.infra.kubernetes.service_account_name") String serviceAccountName,
      @Nullable @Named("che.infra.kubernetes.workspace_sa_cluster_roles") String clusterRoleNames,
      KubernetesClientFactory clientFactory) {
    this.clientFactory = clientFactory;
    this.serviceAccountName = serviceAccountName;
    if (!isNullOrEmpty(clusterRoleNames)) {
      this.clusterRoleNames =
          Sets.newHashSet(
              Splitter.on(",").trimResults().omitEmptyStrings().split(clusterRoleNames));
    } else {
      this.clusterRoleNames = Collections.emptySet();
    }
  }

  @Override
  public void configure(NamespaceResolutionContext namespaceResolutionContext, String namespaceName)
      throws InfrastructureException {
    if (!isNullOrEmpty(serviceAccountName)) {
      KubernetesWorkspaceServiceAccount workspaceServiceAccount =
          doCreateServiceAccount(namespaceResolutionContext.getWorkspaceId(), namespaceName);
      workspaceServiceAccount.prepare();
    }
  }

  KubernetesWorkspaceServiceAccount doCreateServiceAccount(
      String workspaceId, String namespaceName) {
    return new KubernetesWorkspaceServiceAccount(
        workspaceId, namespaceName, serviceAccountName, clusterRoleNames, clientFactory);
  }
}
