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
package org.eclipse.che.workspace.infrastructure.kubernetes;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.annotation.Traced;
import org.eclipse.che.commons.tracing.TracingTags;
import org.eclipse.che.workspace.infrastructure.kubernetes.environment.KubernetesEnvironment;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.CertificateProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.GatewayRouterProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.GitConfigProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.ImagePullSecretProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.KubernetesTrustedCAProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.PodTerminationGracePeriodProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.SecurityContextProvisioner;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies the set of configurations to the Kubernetes environment and environment configuration
 * with the desired order, which corresponds to the needs of the Kubernetes infrastructure.
 *
 * @author Anton Korneta
 * @author Alexander Garagatyi
 */
public interface KubernetesEnvironmentProvisioner<T extends KubernetesEnvironment> {

  void provision(T k8sEnv, RuntimeIdentity identity) throws InfrastructureException;

  @Singleton
  class KubernetesEnvironmentProvisionerImpl
      implements KubernetesEnvironmentProvisioner<KubernetesEnvironment> {

    private static final Logger LOG =
        LoggerFactory.getLogger(KubernetesEnvironmentProvisionerImpl.class);

    private final UniqueNamesProvisioner<KubernetesEnvironment> uniqueNamesProvisioner;
    private final ServersConverter<KubernetesEnvironment> serversConverter;
    private final EnvVarsConverter envVarsConverter;
    private final RestartPolicyRewriter restartPolicyRewriter;
    private final ContainerResourceProvisioner resourceLimitRequestProvisioner;
    private final SecurityContextProvisioner securityContextProvisioner;
    private final PodTerminationGracePeriodProvisioner podTerminationGracePeriodProvisioner;
    private final TlsProvisioner<KubernetesEnvironment> externalServerTlsProvisioner;
    private final ImagePullSecretProvisioner imagePullSecretProvisioner;
    private final ServiceAccountProvisioner serviceAccountProvisioner;
    private final CertificateProvisioner certificateProvisioner;
    private final SshKeysProvisioner sshKeysProvisioner;
    private final GitConfigProvisioner gitConfigProvisioner;
    private final PreviewUrlExposer<KubernetesEnvironment> previewUrlExposer;
    private final VcsSslCertificateProvisioner vcsSslCertificateProvisioner;
    private final GatewayRouterProvisioner gatewayRouterProvisioner;
    private final KubernetesTrustedCAProvisioner trustedCAProvisioner;

    @Inject
    public KubernetesEnvironmentProvisionerImpl(
        UniqueNamesProvisioner<KubernetesEnvironment> uniqueNamesProvisioner,
        ServersConverter<KubernetesEnvironment> serversConverter,
        EnvVarsConverter envVarsConverter,
        RestartPolicyRewriter restartPolicyRewriter,
        ContainerResourceProvisioner resourceLimitRequestProvisioner,
        SecurityContextProvisioner securityContextProvisioner,
        PodTerminationGracePeriodProvisioner podTerminationGracePeriodProvisioner,
        TlsProvisionerProvider<KubernetesEnvironment> externalServerTlsProvisionerProvider,
        ImagePullSecretProvisioner imagePullSecretProvisioner,
        ServiceAccountProvisioner serviceAccountProvisioner,
        CertificateProvisioner certificateProvisioner,
        SshKeysProvisioner sshKeysProvisioner,
        GitConfigProvisioner gitConfigProvisioner,
        PreviewUrlExposer<KubernetesEnvironment> previewUrlExposer,
        VcsSslCertificateProvisioner vcsSslCertificateProvisioner,
        GatewayRouterProvisioner gatewayRouterProvisioner,
        KubernetesTrustedCAProvisioner trustedCAProvisioner) {
      this.uniqueNamesProvisioner = uniqueNamesProvisioner;
      this.serversConverter = serversConverter;
      this.envVarsConverter = envVarsConverter;
      this.restartPolicyRewriter = restartPolicyRewriter;
      this.resourceLimitRequestProvisioner = resourceLimitRequestProvisioner;
      this.securityContextProvisioner = securityContextProvisioner;
      this.podTerminationGracePeriodProvisioner = podTerminationGracePeriodProvisioner;
      this.externalServerTlsProvisioner = externalServerTlsProvisionerProvider.get();
      this.imagePullSecretProvisioner = imagePullSecretProvisioner;
      this.serviceAccountProvisioner = serviceAccountProvisioner;
      this.certificateProvisioner = certificateProvisioner;
      this.sshKeysProvisioner = sshKeysProvisioner;
      this.vcsSslCertificateProvisioner = vcsSslCertificateProvisioner;
      this.gitConfigProvisioner = gitConfigProvisioner;
      this.previewUrlExposer = previewUrlExposer;
      this.gatewayRouterProvisioner = gatewayRouterProvisioner;
      this.trustedCAProvisioner = trustedCAProvisioner;
    }

    @Traced
    public void provision(KubernetesEnvironment k8sEnv, RuntimeIdentity identity)
        throws InfrastructureException {
      final String workspaceId = identity.getWorkspaceId();
      TracingTags.WORKSPACE_ID.set(workspaceId);

      LOG.debug("Start provisioning Kubernetes environment for workspace '{}'", workspaceId);

      // 1st stage - converting Che model env to Kubernetes env
      LOG.debug("Provisioning servers & env vars converters for workspace '{}'", workspaceId);
      serversConverter.provision(k8sEnv, identity);
      previewUrlExposer.expose(k8sEnv);
      envVarsConverter.provision(k8sEnv, identity);

      // 2nd stage - add Kubernetes env items
      LOG.debug("Provisioning environment items for workspace '{}'", workspaceId);
      restartPolicyRewriter.provision(k8sEnv, identity);
      resourceLimitRequestProvisioner.provision(k8sEnv, identity);
      externalServerTlsProvisioner.provision(k8sEnv, identity);
      securityContextProvisioner.provision(k8sEnv, identity);
      podTerminationGracePeriodProvisioner.provision(k8sEnv, identity);
      imagePullSecretProvisioner.provision(k8sEnv, identity);
      serviceAccountProvisioner.provision(k8sEnv, identity);
      certificateProvisioner.provision(k8sEnv, identity);
      sshKeysProvisioner.provision(k8sEnv, identity);
      vcsSslCertificateProvisioner.provision(k8sEnv, identity);
      gitConfigProvisioner.provision(k8sEnv, identity);
      gatewayRouterProvisioner.provision(k8sEnv, identity);
      trustedCAProvisioner.provision(k8sEnv, identity);
      uniqueNamesProvisioner.provision(k8sEnv, identity);
      LOG.debug("Provisioning Kubernetes environment done for workspace '{}'", workspaceId);
    }
  }
}
