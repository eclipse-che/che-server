package org.eclipse.che.workspace.infrastructure.kubernetes.namespace.configurator;

import static org.eclipse.che.workspace.infrastructure.kubernetes.Constants.DEV_WORKSPACE_MOUNT_AS_ANNOTATION;
import static org.eclipse.che.workspace.infrastructure.kubernetes.Constants.DEV_WORKSPACE_MOUNT_LABEL;
import static org.eclipse.che.workspace.infrastructure.kubernetes.Constants.DEV_WORKSPACE_MOUNT_PATH_ANNOTATION;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.user.User;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespaceFactory;

public class UserProfileConfigurator implements NamespaceConfigurator {
  private static final String USER_PROFILE_SECRET_NAME = "user-profile";
  private static final String USER_PROFILE_SECRET_MOUNT_PATH = "/config/user/profile";

  private final KubernetesNamespaceFactory namespaceFactory;
  private final KubernetesClientFactory clientFactory;
  private final UserManager userManager;

  @Inject
  public UserProfileConfigurator(
      KubernetesNamespaceFactory namespaceFactory,
      KubernetesClientFactory clientFactory,
      UserManager userManager) {
    this.namespaceFactory = namespaceFactory;
    this.clientFactory = clientFactory;
    this.userManager = userManager;
  }

  @Override
  public void configure(NamespaceResolutionContext namespaceResolutionContext)
      throws InfrastructureException {
    Secret userProfileSecret = prepareProfileSecret(namespaceResolutionContext);
    String namespace = namespaceFactory.evaluateNamespaceName(namespaceResolutionContext);
    try {
      clientFactory.create().secrets().inNamespace(namespace).createOrReplace(userProfileSecret);
    } catch (KubernetesClientException e) {
      throw new InfrastructureException(e);
    }
  }

  private Secret prepareProfileSecret(NamespaceResolutionContext namespaceResolutionContext)
      throws InfrastructureException {
    User user;
    try {
      user = userManager.getById(namespaceResolutionContext.getUserId());
    } catch (NotFoundException | ServerException e) {
      throw new InfrastructureException(e);
    }

    Base64.Encoder enc = Base64.getEncoder();
    final Map<String, String> userProfileData = new HashMap<>();
    userProfileData.put("id", enc.encodeToString(user.getId().getBytes()));
    userProfileData.put("name", enc.encodeToString(user.getName().getBytes()));
    userProfileData.put("email", enc.encodeToString(user.getEmail().getBytes()));

    return new SecretBuilder()
        .addToData(userProfileData)
        .withNewMetadata()
        .withName(USER_PROFILE_SECRET_NAME)
        .addToLabels(DEV_WORKSPACE_MOUNT_LABEL, "true")
        .addToAnnotations(DEV_WORKSPACE_MOUNT_AS_ANNOTATION, "file")
        .addToAnnotations(DEV_WORKSPACE_MOUNT_PATH_ANNOTATION, USER_PROFILE_SECRET_MOUNT_PATH)
        .endMetadata()
        .build();
  }
}
