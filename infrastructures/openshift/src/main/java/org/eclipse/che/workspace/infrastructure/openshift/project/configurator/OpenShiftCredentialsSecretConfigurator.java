package org.eclipse.che.workspace.infrastructure.openshift.project.configurator;

import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.AbstractWorkspaceServiceAccount.CREDENTIALS_SECRET_NAME;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import javax.inject.Inject;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.configurator.NamespaceConfigurator;
import org.eclipse.che.workspace.infrastructure.openshift.OpenShiftClientFactory;

public class OpenShiftCredentialsSecretConfigurator implements NamespaceConfigurator {
  private final OpenShiftClientFactory clientFactory;

  @Inject
  public OpenShiftCredentialsSecretConfigurator(OpenShiftClientFactory clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public void configure(NamespaceResolutionContext namespaceResolutionContext, String namespaceName)
      throws InfrastructureException {
    // create credentials secret
    if (clientFactory
            .createOC()
            .secrets()
            .inNamespace(namespaceName)
            .withName(CREDENTIALS_SECRET_NAME)
            .get()
        == null) {
      Secret secret =
          new SecretBuilder()
              .withType("opaque")
              .withNewMetadata()
              .withName(CREDENTIALS_SECRET_NAME)
              .endMetadata()
              .build();
      clientFactory.createOC().secrets().inNamespace(namespaceName).create(secret);
    }
  }
}
