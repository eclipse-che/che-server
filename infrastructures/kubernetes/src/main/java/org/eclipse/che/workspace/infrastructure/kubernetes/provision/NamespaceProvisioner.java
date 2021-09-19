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
package org.eclipse.che.workspace.infrastructure.kubernetes.provision;

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
import org.eclipse.che.api.core.notification.EventSubscriber;
import org.eclipse.che.api.user.server.PreferenceManager;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.api.user.server.event.PostUserPersistedEvent;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.api.shared.KubernetesNamespaceMeta;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespaceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NamespaceProvisioner implements EventSubscriber<PostUserPersistedEvent> {
  private static final Logger LOG = LoggerFactory.getLogger(NamespaceProvisioner.class);
  private static final String USER_PROFILE_SECRET_NAME = "user-profile";
  private static final String USER_PREFERENCES_SECRET_NAME = "user-preferences";
  private static final String USER_PROFILE_SECRET_MOUNT_PATH = "/config/user/profile";
  private static final String USER_PREFERENCES_SECRET_MOUNT_PATH = "/config/user/preferences";

  private final KubernetesNamespaceFactory namespaceFactory;
  private final PreferenceManager preferenceManager;
  private final KubernetesClientFactory clientFactory;
  private final UserManager userManager;

  @Inject
  public NamespaceProvisioner(
      KubernetesNamespaceFactory namespaceFactory,
      KubernetesClientFactory clientFactory,
      UserManager userManager,
      PreferenceManager preferenceManager) {
    this.namespaceFactory = namespaceFactory;
    this.clientFactory = clientFactory;
    this.userManager = userManager;
    this.preferenceManager = preferenceManager;
  }

  public KubernetesNamespaceMeta provision(NamespaceResolutionContext namespaceResolutionContext)
      throws InfrastructureException {

    KubernetesNamespaceMeta kubernetesNamespaceMeta =
        namespaceFactory.provision(namespaceResolutionContext);
    try {
      createOrUpdateSecrets(userManager.getById(namespaceResolutionContext.getUserId()));
    } catch (NotFoundException | ServerException e) {
      LOG.error("Could not find current user. Skipping creation of user information secrets.", e);
    }
    return kubernetesNamespaceMeta;
  };

  @Override
  public void onEvent(PostUserPersistedEvent event) {
    createOrUpdateSecrets(event.getUser());
  }

  private void createOrUpdateSecrets(User user) {

    Secret userProfileSecret = prepareProfileSecret(user);
    Secret userPreferencesSecret = preparePreferencesSecret(user);

    try {
      String namespace =
              namespaceFactory.evaluateNamespaceName(
                      new NamespaceResolutionContext(null, user.getId(), user.getName()));

      clientFactory.create().secrets().inNamespace(namespace).createOrReplace(userProfileSecret);
      if (userPreferencesSecret != null) {
        clientFactory.create().secrets().inNamespace(namespace).createOrReplace(userPreferencesSecret);
      }
    } catch (InfrastructureException | KubernetesClientException e) {
      LOG.error("There was a failure while creating user information secrets.", e);
    }

  }

  private Secret prepareProfileSecret(User user) {
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

  private Secret preparePreferencesSecret(User user) {
    Base64.Encoder enc = Base64.getEncoder();
    Map<String, String> preferences;
    try {
      preferences = preferenceManager.find(user.getId());
    } catch (ServerException e) {
      LOG.error(
          "Could not find user preferences. Skipping creation of user preferences secrets.", e);
      return null;
    }
    if (preferences == null || preferences.isEmpty()){
      LOG.error(
              "User preferences are empty. Skipping creation of user preferences secrets.");
      return null;
    }

    Map<String, String> preferencesEncoded = new HashMap<>();
    preferences.forEach(
        (key, value) ->
            preferencesEncoded.put(normalizeDataKey(key), enc.encodeToString(value.getBytes())));

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
   * Some preferences names are not compatible with k8s restrictions on key field in secret. This
   * method replaces illegal characters with "-" (dash).
   *
   * @param name original preference name
   * @return k8s compatible preference name used as a key field in Secret
   */
  @VisibleForTesting
  String normalizeDataKey(String name) {
    return name.replaceAll("[^-._a-zA-Z0-9]+", "-").replaceAll("-+", "-");
  }
}
