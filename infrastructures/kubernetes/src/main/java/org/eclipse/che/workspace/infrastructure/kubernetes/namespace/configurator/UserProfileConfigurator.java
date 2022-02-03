/*
 * Copyright (c) 2012-2022 Red Hat, Inc.
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
import static org.eclipse.che.workspace.infrastructure.kubernetes.Constants.DEV_WORKSPACE_WATCH_SECRET_LABEL;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.user.User;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;

/**
 * Creates {@link Secret} with user profile information such as his id, name and email. This serves
 * as a way for DevWorkspaces to acquire information about the user.
 *
 * @author Pavol Baran
 */
@Singleton
public class UserProfileConfigurator implements NamespaceConfigurator {
  private static final String USER_PROFILE_SECRET_NAME = "user-profile";
  private static final String USER_PROFILE_SECRET_MOUNT_PATH = "/config/user/profile";

  private final KubernetesClientFactory clientFactory;
  private final UserManager userManager;

  @Inject
  public UserProfileConfigurator(KubernetesClientFactory clientFactory, UserManager userManager) {
    this.clientFactory = clientFactory;
    this.userManager = userManager;
  }

  @Override
  public void configure(NamespaceResolutionContext namespaceResolutionContext, String namespaceName)
      throws InfrastructureException {
    Secret userProfileSecret = prepareProfileSecret(namespaceResolutionContext);
    try {
      clientFactory
          .create()
          .secrets()
          .inNamespace(namespaceName)
          .createOrReplace(userProfileSecret);
    } catch (KubernetesClientException e) {
      throw new InfrastructureException(
          "Error occurred while trying to create user profile secret.", e);
    }
  }

  private Secret prepareProfileSecret(NamespaceResolutionContext namespaceResolutionContext)
      throws InfrastructureException {
    User user;
    try {
      user = userManager.getById(namespaceResolutionContext.getUserId());
    } catch (NotFoundException | ServerException e) {
      throw new InfrastructureException(
          String.format(
              "Could not find current user with id:%s.", namespaceResolutionContext.getUserId()),
          e);
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
        .addToLabels(DEV_WORKSPACE_WATCH_SECRET_LABEL, "true")
        .addToAnnotations(DEV_WORKSPACE_MOUNT_AS_ANNOTATION, "file")
        .addToAnnotations(DEV_WORKSPACE_MOUNT_PATH_ANNOTATION, USER_PROFILE_SECRET_MOUNT_PATH)
        .endMetadata()
        .build();
  }
}
