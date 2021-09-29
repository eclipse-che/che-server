/*
 * Copyright (c) 2012-2021 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
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

/**
 * Creates {@link Secret} with user preferences. This serves as a way for DevWorkspaces to acquire
 * information about the user.
 *
 * @author Pavol Baran
 */
public class UserPreferencesConfigurator implements NamespaceConfigurator {
  private static final String USER_PREFERENCES_SECRET_NAME = "user-preferences";
  private static final String USER_PREFERENCES_SECRET_MOUNT_PATH = "/config/user/preferences";
  private static final int PREFERENCE_NAME_MAX_LENGTH = 253;

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
      throw new InfrastructureException(
          "Error occurred while trying to create user preferences secret.", e);
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
      throw new InfrastructureException(
          String.format(
              "Preferences of user with id:%s cannot be retrieved.",
              namespaceResolutionContext.getUserId()),
          e);
    }

    if (preferences == null || preferences.isEmpty()) {
      throw new InfrastructureException(
          String.format(
              "Preferences of user with id:%s are empty. Cannot create user preferences secrets.",
              namespaceResolutionContext.getUserId()));
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
    name = name.replaceAll("[^-._a-zA-Z0-9]+", "-").replaceAll("-+", "-");
    return name.substring(0, Math.min(name.length(), PREFERENCE_NAME_MAX_LENGTH));
  }
}
