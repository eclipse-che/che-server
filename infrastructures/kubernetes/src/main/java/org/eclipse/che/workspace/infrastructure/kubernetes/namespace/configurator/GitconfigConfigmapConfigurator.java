/*
 * Copyright (c) 2012-2025 Red Hat, Inc.
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

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import org.eclipse.che.api.factory.server.scm.GitUserData;
import org.eclipse.che.api.factory.server.scm.GitUserDataFetcher;
import org.eclipse.che.api.factory.server.scm.exception.ScmBadRequestException;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmConfigurationPersistenceException;
import org.eclipse.che.api.factory.server.scm.exception.ScmItemNotFoundException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.commons.lang.Pair;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitconfigConfigmapConfigurator implements NamespaceConfigurator {
  private final CheServerKubernetesClientFactory cheServerKubernetesClientFactory;
  private final Set<GitUserDataFetcher> gitUserDataFetchers;
  private static final Logger LOG = LoggerFactory.getLogger(GitconfigConfigmapConfigurator.class);
  private static final String CONFIGMAP_DATA_KEY = "gitconfig";
  // TODO: rename to a more generic name, since it is not only for user data
  private static final String GITCONFIG_CONFIGMAP_NAME = "workspace-userdata-gitconfig-configmap";
  private static final Map<String, String> GITCONFIG_CONFIGMAP_LABELS =
      ImmutableMap.of(
          "controller.devfile.io/mount-to-devworkspace",
          "true",
          "controller.devfile.io/watch-configmap",
          "true");
  private static final Map<String, String> GITCONFIG_CONFIGMAP_ANNOTATIONS =
      ImmutableMap.of(
          "controller.devfile.io/mount-as", "subpath", "controller.devfile.io/mount-path", "/etc");
  private final Pattern usernmaePattern =
      Pattern.compile("\\[user](.|\\s)*name\\s*=\\s*(?<username>.*)");
  private final Pattern emailPattern =
      Pattern.compile("\\[user](.|\\s)*email\\s*=\\s*(?<email>.*)");
  private final Pattern emptyStringPattern = Pattern.compile("[\"']\\s*[\"']");
  private final Pattern gitconfigSectionPattern =
      Pattern.compile("\\[(?<sectionName>[a-zA-Z0-9]+)](\\n\\s*\\S*\\s*=.*)*");

  @Inject
  public GitconfigConfigmapConfigurator(
      CheServerKubernetesClientFactory cheServerKubernetesClientFactory,
      Set<GitUserDataFetcher> gitUserDataFetchers) {
    this.cheServerKubernetesClientFactory = cheServerKubernetesClientFactory;
    this.gitUserDataFetchers = gitUserDataFetchers;
  }

  @Override
  public void configure(NamespaceResolutionContext namespaceResolutionContext, String namespaceName)
      throws InfrastructureException {
    KubernetesClient client = cheServerKubernetesClientFactory.create();
    Optional<String> gitconfigOptional = getGitconfig(client, namespaceName);
    Optional<Pair<String, String>> usernameAndEmailFromGitconfigOptional = Optional.empty();
    Optional<Pair<String, String>> usernameAndEmailFromFetcherOptional =
        getUsernameAndEmailFromFetcher();
    if (gitconfigOptional.isPresent()) {
      String gitconfig = gitconfigOptional.get();
      usernameAndEmailFromGitconfigOptional = getUsernameAndEmailFromGitconfig(gitconfig);
    }
    if (needUpdateGitconfigConfigmap(
        usernameAndEmailFromGitconfigOptional, usernameAndEmailFromFetcherOptional)) {
      ConfigMap gitconfigConfigmap = buildGitconfigConfigmap();
      Optional<Pair<String, String>> usernameAndEmailOptional =
          usernameAndEmailFromGitconfigOptional.isPresent()
              ? usernameAndEmailFromGitconfigOptional
              : usernameAndEmailFromFetcherOptional;
      Optional<String> gitconfigSectionsOptional =
          generateGitconfigSections(gitconfigOptional, usernameAndEmailOptional);
      gitconfigConfigmap.setData(
          ImmutableMap.of(CONFIGMAP_DATA_KEY, gitconfigSectionsOptional.orElse("")));
      client.configMaps().inNamespace(namespaceName).createOrReplace(gitconfigConfigmap);
    }
  }

  private ConfigMap buildGitconfigConfigmap() {
    return new ConfigMapBuilder()
        .withNewMetadata()
        .withName(GITCONFIG_CONFIGMAP_NAME)
        .withLabels(GITCONFIG_CONFIGMAP_LABELS)
        .withAnnotations(GITCONFIG_CONFIGMAP_ANNOTATIONS)
        .endMetadata()
        .build();
  }

  private boolean needUpdateGitconfigConfigmap(
      Optional<Pair<String, String>> usernameAndEmailFromGitconfigOptional,
      Optional<Pair<String, String>> usernameAndEmailFromFetcher) {
    return usernameAndEmailFromGitconfigOptional.isEmpty()
        && usernameAndEmailFromFetcher.isPresent();
  }

  private Optional<String> generateGitconfigSections(
      Optional<String> gitconfigOptional, Optional<Pair<String, String>> usernameAndEmailOptional) {
    Optional<String> userSectionOptional =
        usernameAndEmailOptional.map(p -> generateUserSection(p.first, p.second));
    StringJoiner joiner = new StringJoiner("\n");
    userSectionOptional.ifPresent(joiner::add);
    gitconfigOptional.flatMap(this::getOtherStoredSections).ifPresent(joiner::add);
    return joiner.length() > 0 ? Optional.of(joiner.toString()) : Optional.empty();
  }

  Optional<String> getOtherStoredSections(String gitconfig) {
    StringJoiner joiner = new StringJoiner("\n");
    Matcher matcher = gitconfigSectionPattern.matcher(gitconfig);
    while (matcher.find()) {
      String sectionName = matcher.group("sectionName");
      if (!sectionName.equals("user") && !sectionName.equals("http")) {
        joiner.add(matcher.group());
      }
    }
    return joiner.length() > 0 ? Optional.of(joiner.toString()) : Optional.empty();
  }

  private Optional<Pair<String, String>> getUsernameAndEmailFromGitconfig(String gitconfig) {
    if (gitconfig.contains("[user]")) {
      Matcher usernameMatcher = usernmaePattern.matcher(gitconfig);
      Matcher emailaMatcher = emailPattern.matcher(gitconfig);
      if (usernameMatcher.find() && emailaMatcher.find()) {
        String username = usernameMatcher.group("username");
        String email = emailaMatcher.group("email");
        if (!emptyStringPattern.matcher(username).matches()
            && !emptyStringPattern.matcher(email).matches()) {
          return Optional.of(new Pair<>(username, email));
        }
      }
    }
    return Optional.empty();
  }

  private Optional<Pair<String, String>> getUsernameAndEmailFromFetcher() {
    GitUserData gitUserData;
    for (GitUserDataFetcher fetcher : gitUserDataFetchers) {
      try {
        gitUserData = fetcher.fetchGitUserData();
        if (!isNullOrEmpty(gitUserData.getScmUsername())
            && !isNullOrEmpty(gitUserData.getScmUserEmail())) {
          return Optional.of(
              new Pair<>(gitUserData.getScmUsername(), gitUserData.getScmUserEmail()));
        }
      } catch (ScmUnauthorizedException
          | ScmCommunicationException
          | ScmConfigurationPersistenceException
          | ScmItemNotFoundException
          | ScmBadRequestException e) {
        LOG.debug("No GitUserDataFetcher is configured. " + e.getMessage());
      }
    }
    return Optional.empty();
  }

  private String generateUserSection(String username, String email) {
    return String.format("[user]\n\tname = %1$s\n\temail = %2$s", username, email);
  }

  private Optional<String> getGitconfig(KubernetesClient client, String namespaceName) {
    ConfigMap gitconfigConfigmap =
        client.configMaps().inNamespace(namespaceName).withName(GITCONFIG_CONFIGMAP_NAME).get();
    if (gitconfigConfigmap != null) {
      String gitconfig = gitconfigConfigmap.getData().get(CONFIGMAP_DATA_KEY);
      if (!isNullOrEmpty(gitconfig)) {
        return Optional.of(gitconfig);
      }
    }
    return Optional.empty();
  }
}
