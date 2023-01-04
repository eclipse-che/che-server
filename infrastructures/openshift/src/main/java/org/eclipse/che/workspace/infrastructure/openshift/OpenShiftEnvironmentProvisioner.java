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
package org.eclipse.che.workspace.infrastructure.openshift;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.annotation.Traced;
import org.eclipse.che.commons.tracing.TracingTags;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesEnvironmentProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.CertificateProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.DeploymentMetadataProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.GatewayRouterProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.GitConfigProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.ImagePullSecretProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.PodTerminationGracePeriodProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.ServiceAccountProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.SshKeysProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.TlsProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.TlsProvisionerProvider;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.UniqueNamesProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.VcsSslCertificateProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.env.EnvVarsConverter;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.limits.ram.ContainerResourceProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.restartpolicy.RestartPolicyRewriter;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.server.ServersConverter;
import org.eclipse.che.workspace.infrastructure.kubernetes.server.PreviewUrlExposer;
import org.eclipse.che.workspace.infrastructure.openshift.environment.OpenShiftEnvironment;
import org.eclipse.che.workspace.infrastructure.openshift.provision.OpenShiftUniqueNamesProvisioner;
import org.eclipse.che.workspace.infrastructure.openshift.provision.OpenshiftTrustedCAProvisioner;
import org.eclipse.che.workspace.infrastructure.openshift.server.OpenShiftPreviewUrlExposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies the set of configurations to the OpenShift environment and environment configuration with
 * the desired order, which corresponds to the needs of the OpenShift infrastructure.
 *
 * @author Anton Korneta
 * @author Alexander Garagatyi
 */
@Singleton
public class OpenShiftEnvironmentProvisioner
    implements KubernetesEnvironmentProvisioner<OpenShiftEnvironment> {

  private static final Logger LOG = LoggerFactory.getLogger(OpenShiftEnvironmentProvisioner.class);

  private final UniqueNamesProvisioner<OpenShiftEnvironment> uniqueNamesProvisioner;
  private final TlsProvisioner<OpenShiftEnvironment> routeTlsProvisioner;
  private final ServersConverter<OpenShiftEnvironment> serversConverter;
  private final EnvVarsConverter envVarsConverter;
  private final RestartPolicyRewriter restartPolicyRewriter;
  private final ContainerResourceProvisioner resourceLimitRequestProvisioner;
  private final PodTerminationGracePeriodProvisioner podTerminationGracePeriodProvisioner;
  private final ImagePullSecretProvisioner imagePullSecretProvisioner;
  private final ServiceAccountProvisioner serviceAccountProvisioner;
  private final CertificateProvisioner certificateProvisioner;
  private final SshKeysProvisioner sshKeysProvisioner;
  private final GitConfigProvisioner gitConfigProvisioner;
  private final PreviewUrlExposer<OpenShiftEnvironment> previewUrlExposer;
  private final VcsSslCertificateProvisioner vcsSslCertificateProvisioner;
  private final GatewayRouterProvisioner gatewayRouterProvisioner;
  private final DeploymentMetadataProvisioner deploymentMetadataProvisioner;
  private final OpenshiftTrustedCAProvisioner trustedCAProvisioner;

  @Inject
  public OpenShiftEnvironmentProvisioner(
      OpenShiftUniqueNamesProvisioner uniqueNamesProvisioner,
      TlsProvisionerProvider<OpenShiftEnvironment> routeTlsProvisionerProvider,
      ServersConverter<OpenShiftEnvironment> serversConverter,
      EnvVarsConverter envVarsConverter,
      RestartPolicyRewriter restartPolicyRewriter,
      ContainerResourceProvisioner resourceLimitRequestProvisioner,
      PodTerminationGracePeriodProvisioner podTerminationGracePeriodProvisioner,
      ImagePullSecretProvisioner imagePullSecretProvisioner,
      ServiceAccountProvisioner serviceAccountProvisioner,
      CertificateProvisioner certificateProvisioner,
      SshKeysProvisioner sshKeysProvisioner,
      GitConfigProvisioner gitConfigProvisioner,
      OpenShiftPreviewUrlExposer previewUrlEndpointsProvisioner,
      VcsSslCertificateProvisioner vcsSslCertificateProvisioner,
      GatewayRouterProvisioner gatewayRouterProvisioner,
      DeploymentMetadataProvisioner deploymentMetadataProvisioner,
      OpenshiftTrustedCAProvisioner trustedCAProvisioner) {
    this.uniqueNamesProvisioner = uniqueNamesProvisioner;
    this.routeTlsProvisioner = routeTlsProvisionerProvider.get();
    this.serversConverter = serversConverter;
    this.envVarsConverter = envVarsConverter;
    this.restartPolicyRewriter = restartPolicyRewriter;
    this.resourceLimitRequestProvisioner = resourceLimitRequestProvisioner;
    this.podTerminationGracePeriodProvisioner = podTerminationGracePeriodProvisioner;
    this.imagePullSecretProvisioner = imagePullSecretProvisioner;
    this.serviceAccountProvisioner = serviceAccountProvisioner;
    this.certificateProvisioner = certificateProvisioner;
    this.sshKeysProvisioner = sshKeysProvisioner;
    this.gitConfigProvisioner = gitConfigProvisioner;
    this.previewUrlExposer = previewUrlEndpointsProvisioner;
    this.vcsSslCertificateProvisioner = vcsSslCertificateProvisioner;
    this.gatewayRouterProvisioner = gatewayRouterProvisioner;
    this.deploymentMetadataProvisioner = deploymentMetadataProvisioner;
    this.trustedCAProvisioner = trustedCAProvisioner;
  }

  @Override
  @Traced
  public void provision(OpenShiftEnvironment osEnv, RuntimeIdentity identity)
      throws InfrastructureException {
    TracingTags.WORKSPACE_ID.set(identity::getWorkspaceId);

    LOG.debug(
        "Start provisioning OpenShift environment for workspace '{}'", identity.getWorkspaceId());

    // 1st stage - converting Che model env to OpenShift env
    serversConverter.provision(osEnv, identity);
    previewUrlExposer.expose(osEnv);
    envVarsConverter.provision(osEnv, identity);

    // 2nd stage - add OpenShift env items
    restartPolicyRewriter.provision(osEnv, identity);
    routeTlsProvisioner.provision(osEnv, identity);
    resourceLimitRequestProvisioner.provision(osEnv, identity);
    podTerminationGracePeriodProvisioner.provision(osEnv, identity);
    imagePullSecretProvisioner.provision(osEnv, identity);
    serviceAccountProvisioner.provision(osEnv, identity);
    certificateProvisioner.provision(osEnv, identity);
    sshKeysProvisioner.provision(osEnv, identity);
    vcsSslCertificateProvisioner.provision(osEnv, identity);
    gitConfigProvisioner.provision(osEnv, identity);
    gatewayRouterProvisioner.provision(osEnv, identity);
    deploymentMetadataProvisioner.provision(osEnv, identity);
    trustedCAProvisioner.provision(osEnv, identity);
    uniqueNamesProvisioner.provision(osEnv, identity);
    LOG.debug(
        "Provisioning OpenShift environment done for workspace '{}'", identity.getWorkspaceId());
  }
}
