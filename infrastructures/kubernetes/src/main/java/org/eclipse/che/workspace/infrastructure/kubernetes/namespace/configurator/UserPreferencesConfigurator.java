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

import com.google.common.annotations.VisibleForTesting;
import io.fabric8.kubernetes.api.model.Secret;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.user.server.PreferenceManager;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;

/**
 * Creates {@link Secret} with user preferences. This serves as a way for DevWorkspaces to acquire
 * information about the user.
 *
 * @author Pavol Baran
 */
@Deprecated
@Singleton
public class UserPreferencesConfigurator implements NamespaceConfigurator {
  private static final String USER_PREFERENCES_SECRET_NAME = "user-preferences";
  private static final String USER_PREFERENCES_SECRET_MOUNT_PATH = "/config/user/preferences";
  private static final int PREFERENCE_NAME_MAX_LENGTH = 253;

  private final KubernetesClientFactory clientFactory;
  private final UserManager userManager;
  private final PreferenceManager preferenceManager;

  @Inject
  public UserPreferencesConfigurator(
      KubernetesClientFactory clientFactory,
      UserManager userManager,
      PreferenceManager preferenceManager) {
    this.clientFactory = clientFactory;
    this.userManager = userManager;
    this.preferenceManager = preferenceManager;
  }

  /** 'user-preferences' secret is obsolete and not used anymore by DevWorkspaces */
  @Override
  public void configure(NamespaceResolutionContext namespaceResolutionContext, String namespaceName)
      throws InfrastructureException {}

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
