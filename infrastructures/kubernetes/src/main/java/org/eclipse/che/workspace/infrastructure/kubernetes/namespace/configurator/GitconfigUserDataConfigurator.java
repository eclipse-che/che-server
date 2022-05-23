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

import static java.util.Collections.singletonMap;
import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.AbstractWorkspaceServiceAccount.GIT_USERDATA_CONFIGMAP_NAME;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import org.eclipse.che.api.factory.server.scm.GitUserData;
import org.eclipse.che.api.factory.server.scm.GitUserDataFetcher;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitconfigUserDataConfigurator implements NamespaceConfigurator {
  private static final Logger LOG = LoggerFactory.getLogger(GitconfigUserDataConfigurator.class);
  private final KubernetesClientFactory clientFactory;
  private final Set<GitUserDataFetcher> gitUserDataFetchers;
  private static final String CONFIGMAP_DATA_KEY = "gitconfig";

  @Inject
  public GitconfigUserDataConfigurator(
      KubernetesClientFactory clientFactory, Set<GitUserDataFetcher> gitUserDataFetchers) {
    this.clientFactory = clientFactory;
    this.gitUserDataFetchers = gitUserDataFetchers;
  }

  @Override
  public void configure(NamespaceResolutionContext namespaceResolutionContext, String namespaceName)
      throws InfrastructureException {
    var client = clientFactory.create();
    GitUserData gitUserData = null;
    for (GitUserDataFetcher fetcher : gitUserDataFetchers) {
      try {
        gitUserData = fetcher.fetchGitUserData();
        break;
      } catch (ScmUnauthorizedException | ScmCommunicationException e) {
        LOG.debug("No GitUserDataFetcher is configured. " + e.getMessage());
      }
    }

    Map<String, String> annotations =
        ImmutableMap.of(
            "controller.devfile.io/mount-as",
            "subpath",
            "controller.devfile.io/mount-path",
            "/etc/");
    Map<String, String> labels =
        ImmutableMap.of(
            "controller.devfile.io/mount-to-devworkspace",
            "true",
            "controller.devfile.io/watch-configmap",
            "true");
    if (gitUserData != null
        && client
                .configMaps()
                .inNamespace(namespaceName)
                .withName(GIT_USERDATA_CONFIGMAP_NAME)
                .get()
            == null
        && client
            .configMaps()
            .inNamespace(namespaceName)
            .withLabels(labels)
            .list()
            .getItems()
            .stream()
            .noneMatch(
                configMap ->
                    configMap
                            .getMetadata()
                            .getAnnotations()
                            .entrySet()
                            .containsAll(annotations.entrySet())
                        && configMap.getData().containsKey(CONFIGMAP_DATA_KEY))) {
      ConfigMap configMap =
          new ConfigMapBuilder()
              .withNewMetadata()
              .withName(GIT_USERDATA_CONFIGMAP_NAME)
              .withLabels(labels)
              .withAnnotations(annotations)
              .endMetadata()
              .build();
      configMap.setData(
          singletonMap(
              CONFIGMAP_DATA_KEY,
              String.format(
                  "[user]\n\tname = %1$s\n\temail = %2$s",
                  gitUserData.getScmUsername(), gitUserData.getScmUserEmail())));
      client.configMaps().inNamespace(namespaceName).create(configMap);
    }
  }
}
