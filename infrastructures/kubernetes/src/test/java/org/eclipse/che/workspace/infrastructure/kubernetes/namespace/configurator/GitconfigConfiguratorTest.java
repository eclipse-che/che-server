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

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.che.api.factory.server.scm.GitUserData;
import org.eclipse.che.api.factory.server.scm.GitUserDataFetcher;
import org.eclipse.che.api.factory.server.scm.exception.*;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class GitconfigConfiguratorTest {

  private NamespaceConfigurator configurator;

  @Mock private CheServerKubernetesClientFactory cheServerKubernetesClientFactory;
  @Mock private GitUserDataFetcher gitUserDataFetcher;
  private KubernetesServer serverMock;

  private NamespaceResolutionContext namespaceResolutionContext;
  private final String TEST_NAMESPACE_NAME = "namespace123";
  private final String TEST_WORKSPACE_ID = "workspace123";
  private final String TEST_USER_ID = "user123";
  private final String TEST_USERNAME = "username123";
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

  @BeforeMethod
  public void setUp()
      throws InfrastructureException, ScmCommunicationException, ScmUnauthorizedException {
    configurator =
        new GitconfigConfigurator(cheServerKubernetesClientFactory, Collections.emptySet());

    serverMock = new KubernetesServer(true, true);
    serverMock.before();
    KubernetesClient client = spy(serverMock.getClient());
    when(cheServerKubernetesClientFactory.create()).thenReturn(client);

    namespaceResolutionContext =
        new NamespaceResolutionContext(TEST_WORKSPACE_ID, TEST_USER_ID, TEST_USERNAME);
  }

  @Test
  public void shouldCreateGitconfigConfigmapWithUserSection() throws Exception {
    // given
    when(gitUserDataFetcher.fetchGitUserData())
        .thenReturn(new GitUserData("username", "userEmail"));
    configurator =
        new GitconfigConfigurator(cheServerKubernetesClientFactory, Set.of(gitUserDataFetcher));
    // when
    configurator.configure(namespaceResolutionContext, TEST_NAMESPACE_NAME);
    // then
    Assert.assertEquals(serverMock.getLastRequest().getMethod(), "POST");
    List<ConfigMap> configMaps =
        serverMock.getClient().configMaps().inNamespace(TEST_NAMESPACE_NAME).list().getItems();
    Assert.assertEquals(configMaps.size(), 1);
    String expected = "[user]\n\tname = username\n\temail = userEmail";
    Assert.assertEquals(configMaps.get(0).getData().get("gitconfig"), expected);
  }

  @Test
  public void shouldNotCreateGitconfigConfigmap() throws Exception {
    // when
    configurator.configure(namespaceResolutionContext, TEST_NAMESPACE_NAME);
    // then
    Assert.assertEquals(serverMock.getLastRequest().getMethod(), "GET");
    List<ConfigMap> configMaps =
        serverMock.getClient().configMaps().inNamespace(TEST_NAMESPACE_NAME).list().getItems();
    Assert.assertEquals(configMaps.size(), 0);
  }

  @Test
  public void shouldUpdateGitconfigConfigmapWithUserSection()
      throws InfrastructureException, InterruptedException, ScmItemNotFoundException,
          ScmCommunicationException, ScmUnauthorizedException, ScmConfigurationPersistenceException,
          ScmBadRequestException {
    // given
    ConfigMap gitconfigConfigmap =
        new ConfigMapBuilder()
            .withNewMetadata()
            .withName(GITCONFIG_CONFIGMAP_NAME)
            .withLabels(GITCONFIG_CONFIGMAP_LABELS)
            .withAnnotations(GITCONFIG_CONFIGMAP_ANNOTATIONS)
            .endMetadata()
            .build();
    gitconfigConfigmap.setData(
        Collections.singletonMap("gitconfig", "[user]\n\tname = \"\"\n\temail= \"\""));
    serverMock.getClient().configMaps().inNamespace(TEST_NAMESPACE_NAME).create(gitconfigConfigmap);
    configurator =
        new GitconfigConfigurator(cheServerKubernetesClientFactory, Set.of(gitUserDataFetcher));
    when(gitUserDataFetcher.fetchGitUserData())
        .thenReturn(new GitUserData("username", "userEmail"));
    // when
    configurator.configure(namespaceResolutionContext, TEST_NAMESPACE_NAME);
    // then
    Assert.assertEquals(serverMock.getLastRequest().getMethod(), "PUT");
    List<ConfigMap> configMaps =
        serverMock.getClient().configMaps().inNamespace(TEST_NAMESPACE_NAME).list().getItems();
    Assert.assertEquals(configMaps.size(), 1);
    String expected = "[user]\n\tname = username\n\temail = userEmail";
    Assert.assertEquals(configMaps.get(0).getData().get("gitconfig"), expected);
  }

  @Test
  public void shouldUpdateGitconfigConfigmapWithStoredSectionsWithUserSection()
      throws InfrastructureException, InterruptedException, ScmItemNotFoundException,
          ScmCommunicationException, ScmUnauthorizedException, ScmConfigurationPersistenceException,
          ScmBadRequestException {
    // given
    ConfigMap gitconfigConfigmap =
        new ConfigMapBuilder()
            .withNewMetadata()
            .withName(GITCONFIG_CONFIGMAP_NAME)
            .withLabels(GITCONFIG_CONFIGMAP_LABELS)
            .withAnnotations(GITCONFIG_CONFIGMAP_ANNOTATIONS)
            .endMetadata()
            .build();
    gitconfigConfigmap.setData(
        Collections.singletonMap(
            "gitconfig",
            "[other]\n\tkey = value\n[other1]\n\tkey = value\n[user]\n\tname = \"\"\n\temail= \"\""));
    serverMock.getClient().configMaps().inNamespace(TEST_NAMESPACE_NAME).create(gitconfigConfigmap);
    configurator =
        new GitconfigConfigurator(cheServerKubernetesClientFactory, Set.of(gitUserDataFetcher));
    when(gitUserDataFetcher.fetchGitUserData())
        .thenReturn(new GitUserData("username", "userEmail"));
    // when
    configurator.configure(namespaceResolutionContext, TEST_NAMESPACE_NAME);
    // then
    Assert.assertEquals(serverMock.getLastRequest().getMethod(), "PUT");
    List<ConfigMap> configMaps =
        serverMock.getClient().configMaps().inNamespace(TEST_NAMESPACE_NAME).list().getItems();
    Assert.assertEquals(configMaps.size(), 1);
    String expected =
        "[user]\n\tname = username\n\temail = userEmail\n[other]\n\tkey = value\n[other1]\n\tkey = value";
    Assert.assertEquals(configMaps.get(0).getData().get("gitconfig"), expected);
  }

  @Test
  public void shouldNotUpdateGitconfigConfigmapWithUserSection()
      throws InfrastructureException, InterruptedException, ScmItemNotFoundException,
          ScmCommunicationException, ScmUnauthorizedException, ScmConfigurationPersistenceException,
          ScmBadRequestException {
    // given
    ConfigMap gitconfigConfigmap =
        new ConfigMapBuilder()
            .withNewMetadata()
            .withName(GITCONFIG_CONFIGMAP_NAME)
            .withLabels(GITCONFIG_CONFIGMAP_LABELS)
            .withAnnotations(GITCONFIG_CONFIGMAP_ANNOTATIONS)
            .endMetadata()
            .build();
    gitconfigConfigmap.setData(
        Collections.singletonMap(
            "gitconfig", "[user]\n\tname = gitconfig-username\n\temail = gitconfig-email"));
    serverMock.getClient().configMaps().inNamespace(TEST_NAMESPACE_NAME).create(gitconfigConfigmap);
    configurator =
        new GitconfigConfigurator(cheServerKubernetesClientFactory, Set.of(gitUserDataFetcher));
    when(gitUserDataFetcher.fetchGitUserData())
        .thenReturn(new GitUserData("fetcher-username", "fetcher-userEmail"));
    // when
    configurator.configure(namespaceResolutionContext, TEST_NAMESPACE_NAME);
    // then
    Assert.assertEquals(serverMock.getLastRequest().getMethod(), "GET");
    List<ConfigMap> configMaps =
        serverMock.getClient().configMaps().inNamespace(TEST_NAMESPACE_NAME).list().getItems();
    Assert.assertEquals(configMaps.size(), 1);
    String expected = "[user]\n\tname = gitconfig-username\n\temail = gitconfig-email";
    Assert.assertEquals(configMaps.get(0).getData().get("gitconfig"), expected);
  }
}
