package org.eclipse.che.workspace.infrastructure.openshift.project.configurator;

import static com.google.common.base.Strings.isNullOrEmpty;

import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.configurator.NamespaceConfigurator;
import org.eclipse.che.workspace.infrastructure.openshift.provision.OpenShiftStopWorkspaceRoleProvisioner;

public class OpenShiftStopWorkspaceRoleconfigurator implements NamespaceConfigurator {

  private final OpenShiftStopWorkspaceRoleProvisioner stopWorkspaceRoleProvisioner;
  private final String oAuthIdentityProvider;

  @Inject
  public OpenShiftStopWorkspaceRoleconfigurator(
      OpenShiftStopWorkspaceRoleProvisioner stopWorkspaceRoleProvisioner,
      @Nullable @Named("che.infra.openshift.oauth_identity_provider")
          String oAuthIdentityProvider) {
    this.stopWorkspaceRoleProvisioner = stopWorkspaceRoleProvisioner;
    this.oAuthIdentityProvider = oAuthIdentityProvider;
  }

  @Override
  public void configure(NamespaceResolutionContext namespaceResolutionContext, String namespaceName)
      throws InfrastructureException {
    if (!isNullOrEmpty(oAuthIdentityProvider)) {
      stopWorkspaceRoleProvisioner.provision(namespaceName);
    }
  }
}
