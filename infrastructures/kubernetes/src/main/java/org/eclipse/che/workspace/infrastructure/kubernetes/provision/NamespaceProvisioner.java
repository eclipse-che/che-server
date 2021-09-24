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
import java.util.Optional;
import javax.inject.Inject;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.user.User;
import org.eclipse.che.api.user.server.PreferenceManager;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.api.workspace.server.model.impl.RuntimeIdentityImpl;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.api.shared.KubernetesNamespaceMeta;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespace;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespaceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * On namespace provisioning, creates k8s {@link Secret} profile and preferences from information
 * about the User.
 *
 * @author Pavol Baran
 */
public class NamespaceProvisioner {
  private static final Logger LOG = LoggerFactory.getLogger(NamespaceProvisioner.class);

  private final KubernetesNamespaceFactory namespaceFactory;
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
    KubernetesNamespace namespace =
            namespaceFactory.getOrCreate(
                    new RuntimeIdentityImpl(
                            null,
                            null,
                            namespaceResolutionContext.getUserId(),
                            namespaceFactory.evaluateNamespaceName(namespaceResolutionContext)));

    KubernetesNamespaceMeta namespaceMeta = namespaceFactory.fetchNamespace(namespace.getName())
            .orElseThrow(
                    () -> new InfrastructureException("Not able to find namespace " + namespace.getName()));

    try {
      createOrUpdateSecrets(userManager.getById(namespaceResolutionContext.getUserId()));
    } catch (NotFoundException | ServerException e) {
      throw new InfrastructureException(
              "Could not find current user. Because of this, cannot create user profile and preferences secrets.",
              e);
    }
    return namespaceMeta;
  }

  /**
   * Creates k8s user profile and user preferences k8s secrets. This serves as a way for
   * DevWorkspaces to acquire information about the user.
   *
   * @param user from information about this user are the secrets created
   */
  private void createOrUpdateSecrets(User user) {
    Optional<Secret> userPreferencesSecret = preparePreferencesSecret(user);

    try {
      if (userPreferencesSecret.isPresent()) {
        clientFactory
            .create()
            .secrets()
            .inNamespace(namespace)
            .createOrReplace(userPreferencesSecret.get());
      }
    } catch (InfrastructureException | KubernetesClientException e) {
      LOG.error("There was a failure while creating user information secrets.", e);
    }
  }
}
