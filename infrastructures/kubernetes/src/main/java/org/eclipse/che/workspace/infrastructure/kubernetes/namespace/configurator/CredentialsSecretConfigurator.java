package org.eclipse.che.workspace.infrastructure.kubernetes.namespace.configurator;

import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.AbstractWorkspaceServiceAccount.CREDENTIALS_SECRET_NAME;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import javax.inject.Inject;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;

public class CredentialsSecretConfigurator implements NamespaceConfigurator {
  private final KubernetesClientFactory clientFactory;

  @Inject
  public CredentialsSecretConfigurator(KubernetesClientFactory clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public void configure(NamespaceResolutionContext namespaceResolutionContext, String namespaceName)
      throws InfrastructureException {
    if (clientFactory
        .create()
        .secrets()
        .inNamespace(namespaceName)
        .list()
        .getItems()
        .stream()
        .noneMatch(s -> s.getMetadata().getName().equals(CREDENTIALS_SECRET_NAME))) {
      Secret secret =
          new SecretBuilder()
              .withType("opaque")
              .withNewMetadata()
              .withName(CREDENTIALS_SECRET_NAME)
              .endMetadata()
              .build();
      clientFactory.create().secrets().inNamespace(namespaceName).create(secret);
    }
  }
}
