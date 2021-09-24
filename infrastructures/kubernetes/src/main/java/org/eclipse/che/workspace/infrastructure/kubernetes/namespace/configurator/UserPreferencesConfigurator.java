package org.eclipse.che.workspace.infrastructure.kubernetes.namespace.configurator;

import static org.eclipse.che.workspace.infrastructure.kubernetes.Constants.DEV_WORKSPACE_MOUNT_AS_ANNOTATION;
import static org.eclipse.che.workspace.infrastructure.kubernetes.Constants.DEV_WORKSPACE_MOUNT_LABEL;
import static org.eclipse.che.workspace.infrastructure.kubernetes.Constants.DEV_WORKSPACE_MOUNT_PATH_ANNOTATION;

import com.google.common.annotations.VisibleForTesting;
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
import org.eclipse.che.api.user.server.PreferenceManager;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespaceFactory;

public class UserPreferencesConfigurator implements NamespaceConfigurator {
  private static final String USER_PREFERENCES_SECRET_NAME = "user-preferences";
  private static final String USER_PREFERENCES_SECRET_MOUNT_PATH = "/config/user/preferences";

  private final KubernetesNamespaceFactory namespaceFactory;
  private final KubernetesClientFactory clientFactory;
  private final UserManager userManager;
  private final PreferenceManager preferenceManager;

  @Inject
  public UserPreferencesConfigurator(
      KubernetesNamespaceFactory namespaceFactory,
      KubernetesClientFactory clientFactory,
      UserManager userManager,
      PreferenceManager preferenceManager) {
    this.namespaceFactory = namespaceFactory;
    this.clientFactory = clientFactory;
    this.userManager = userManager;
    this.preferenceManager = preferenceManager;
  }

  @Override
  public void configure(NamespaceResolutionContext namespaceResolutionContext)
      throws InfrastructureException {
    Secret userPreferencesSecret = preparePreferencesSecret(namespaceResolutionContext);
    String namespace = namespaceFactory.evaluateNamespaceName(namespaceResolutionContext);

    try {
      clientFactory
          .create()
          .secrets()
          .inNamespace(namespace)
          .createOrReplace(userPreferencesSecret);
    } catch (KubernetesClientException e) {
      throw new InfrastructureException(e);
    }
  }

  private Secret preparePreferencesSecret(NamespaceResolutionContext namespaceResolutionContext)
      throws InfrastructureException {
    Base64.Encoder enc = Base64.getEncoder();
    User user;
    Map<String, String> preferences;

    try {
      user = userManager.getById(namespaceResolutionContext.getUserId());
      preferences = preferenceManager.find(user.getId());
    } catch (NotFoundException | ServerException e) {
      throw new InfrastructureException(e);
    }
    if (preferences == null || preferences.isEmpty()) {
      throw new InfrastructureException(
          "User preferences are empty. Skipping creation of user preferences secrets.");
    }

    Map<String, String> preferencesEncoded = new HashMap<>();
    preferences.forEach(
        (key, value) ->
            preferencesEncoded.put(
                normalizePreferenceName(key), enc.encodeToString(value.getBytes())));
    return new SecretBuilder()
        .addToData(preferencesEncoded)
        .withNewMetadata()
        .withName(USER_PREFERENCES_SECRET_NAME)
        .addToLabels(DEV_WORKSPACE_MOUNT_LABEL, "true")
        .addToAnnotations(DEV_WORKSPACE_MOUNT_AS_ANNOTATION, "file")
        .addToAnnotations(DEV_WORKSPACE_MOUNT_PATH_ANNOTATION, USER_PREFERENCES_SECRET_MOUNT_PATH)
        .endMetadata()
        .build();
  }

  /**
   * Some preferences names are not compatible with k8s restrictions on key field in secret. The
   * keys of data must consist of alphanumeric characters, -, _ or . This method replaces illegal
   * characters with -
   *
   * @param name original preference name
   * @return k8s compatible preference name used as a key field in Secret
   */
  @VisibleForTesting
  String normalizePreferenceName(String name) {
    return name.replaceAll("[^-._a-zA-Z0-9]+", "-").replaceAll("-+", "-");
  }
}
