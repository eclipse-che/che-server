/*
 * Copyright (c) 2012-2024 Red Hat, Inc.
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

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.Secret;
import java.util.Base64;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmConfigurationPersistenceException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.factory.server.scm.exception.UnsatisfiedScmPreconditionException;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This {@link NamespaceConfigurator} ensures that Secret {@link
 * org.eclipse.che.workspace.infrastructure.kubernetes.namespace.AbstractWorkspaceServiceAccount#CREDENTIALS_SECRET_NAME}
 * is present in the Workspace namespace.
 */
@Singleton
public class CredentialsSecretConfigurator implements NamespaceConfigurator {

  private final CheServerKubernetesClientFactory cheServerKubernetesClientFactory;
  private final PersonalAccessTokenManager personalAccessTokenManager;

  private static final Map<String, String> SEARCH_LABELS =
      ImmutableMap.of(
          "app.kubernetes.io/part-of", "che.eclipse.org",
          "app.kubernetes.io/component", "scm-personal-access-token");
  private static final String ANNOTATION_SCM_URL = "che.eclipse.org/scm-url";
  private static final String MERGED_GIT_CREDENTIALS_SECRET_NAME =
      "devworkspace-merged-git-credentials";

  private static final Logger LOG = LoggerFactory.getLogger(CredentialsSecretConfigurator.class);

  @Inject
  public CredentialsSecretConfigurator(
      CheServerKubernetesClientFactory cheServerKubernetesClientFactory,
      PersonalAccessTokenManager personalAccessTokenManager) {
    this.cheServerKubernetesClientFactory = cheServerKubernetesClientFactory;
    this.personalAccessTokenManager = personalAccessTokenManager;
  }

  @Override
  public void configure(NamespaceResolutionContext namespaceResolutionContext, String namespaceName)
      throws InfrastructureException {
    var client = cheServerKubernetesClientFactory.create();
    Secret mergedCredentialsSecret =
        client
            .secrets()
            .inNamespace(namespaceName)
            .withName(MERGED_GIT_CREDENTIALS_SECRET_NAME)
            .get();

    client.secrets().inNamespace(namespaceName).withLabels(SEARCH_LABELS).list().getItems().stream()
        .filter(
            s ->
                mergedCredentialsSecret == null
                    || !getSecretData(mergedCredentialsSecret, "credentials")
                        .contains(getSecretData(s, "token")))
        .forEach(
            s -> {
              try {
                personalAccessTokenManager.storeGitCredentials(
                    s.getMetadata().getAnnotations().get(ANNOTATION_SCM_URL));
              } catch (ScmCommunicationException
                  | ScmConfigurationPersistenceException
                  | UnsatisfiedScmPreconditionException
                  | ScmUnauthorizedException e) {
                LOG.error(e.getMessage(), e);
              }
            });
  }

  private String getSecretData(Secret secret, String key) {
    return new String(Base64.getDecoder().decode(secret.getData().get(key).getBytes()));
  }
}
