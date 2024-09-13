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
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenFetcher;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmConfigurationPersistenceException;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ensures that OAuth token that are represented by Kubernetes Secrets are valid.
 *
 * @author Anatolii Bazko
 */
@Singleton
public class OAuthTokenSecretsConfigurator implements NamespaceConfigurator {

  private final CheServerKubernetesClientFactory cheServerKubernetesClientFactory;
  private final PersonalAccessTokenManager personalAccessTokenManager;

  private static final String ANNOTATION_SCM_URL = "che.eclipse.org/scm-url";
  private static final String ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME =
      "che.eclipse.org/scm-personal-access-token-name";

  private static final Map<String, String> SEARCH_LABELS =
      ImmutableMap.of(
          "app.kubernetes.io/part-of", "che.eclipse.org",
          "app.kubernetes.io/component", "scm-personal-access-token");

  private static final Logger LOG = LoggerFactory.getLogger(OAuthTokenSecretsConfigurator.class);

  @Inject
  public OAuthTokenSecretsConfigurator(
      CheServerKubernetesClientFactory cheServerKubernetesClientFactory,
      PersonalAccessTokenManager personalAccessTokenManager) {
    this.cheServerKubernetesClientFactory = cheServerKubernetesClientFactory;
    this.personalAccessTokenManager = personalAccessTokenManager;
  }

  @Override
  public void configure(NamespaceResolutionContext namespaceResolutionContext, String namespaceName)
      throws InfrastructureException {
    var client = cheServerKubernetesClientFactory.create();
    client.secrets().inNamespace(namespaceName).withLabels(SEARCH_LABELS).list().getItems().stream()
        .filter(
            s ->
                s.getMetadata().getAnnotations() != null
                    && s.getMetadata().getAnnotations().containsKey(ANNOTATION_SCM_URL)
                    && s.getMetadata()
                        .getAnnotations()
                        .containsKey(ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME)
                    && s.getMetadata()
                        .getAnnotations()
                        .get(ANNOTATION_SCM_PERSONAL_ACCESS_TOKEN_NAME)
                        .startsWith(PersonalAccessTokenFetcher.OAUTH_2_PREFIX))
        .forEach(
            s -> {
              try {
                Subject cheSubject = EnvironmentContext.getCurrent().getSubject();
                personalAccessTokenManager.get(
                    cheSubject, s.getMetadata().getAnnotations().get(ANNOTATION_SCM_URL));
              } catch (ScmConfigurationPersistenceException | ScmCommunicationException e) {
                LOG.error(e.getMessage(), e);
              }
            });
  }
}
