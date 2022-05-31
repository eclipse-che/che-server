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

import static org.eclipse.che.workspace.infrastructure.kubernetes.namespace.AbstractWorkspaceServiceAccount.GIT_USERDATA_CONFIGMAP_NAME;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.user.User;
import org.eclipse.che.api.factory.server.scm.GitUserData;
import org.eclipse.che.api.factory.server.scm.GitUserDataFetcher;
import org.eclipse.che.api.factory.server.scm.exception.ScmCommunicationException;
import org.eclipse.che.api.factory.server.scm.exception.ScmUnauthorizedException;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class GitconfigUserdataConfiguratorTest {

  private NamespaceConfigurator configurator;

  @Mock private KubernetesClientFactory clientFactory;
  @Mock private GitUserDataFetcher gitUserDataFetcher;
  @Mock private UserManager userManager;
  private KubernetesServer serverMock;

  private NamespaceResolutionContext namespaceResolutionContext;
  private final String TEST_NAMESPACE_NAME = "namespace123";
  private final String TEST_WORKSPACE_ID = "workspace123";
  private final String TEST_USER_ID = "user123";
  private final String TEST_USERNAME = "jondoe";

  @BeforeMethod
  public void setUp()
      throws InfrastructureException, ScmCommunicationException, ScmUnauthorizedException {
    configurator =
        new GitconfigUserDataConfigurator(clientFactory, Set.of(gitUserDataFetcher), userManager);

    serverMock = new KubernetesServer(true, true);
    serverMock.before();
    KubernetesClient client = spy(serverMock.getClient());
    when(clientFactory.create()).thenReturn(client);

    namespaceResolutionContext =
        new NamespaceResolutionContext(TEST_WORKSPACE_ID, TEST_USER_ID, TEST_USERNAME);
  }

  @Test
  public void createUserdataConfigmapWhenDoesNotExist()
      throws ScmCommunicationException, ScmUnauthorizedException, InfrastructureException,
          InterruptedException {
    // given
    when(gitUserDataFetcher.fetchGitUserData()).thenReturn(new GitUserData("gitUser", "gitEmail"));

    // when
    configurator.configure(namespaceResolutionContext, TEST_NAMESPACE_NAME);

    // then create a secret
    Assert.assertEquals(serverMock.getLastRequest().getMethod(), "POST");
    Assert.assertNotNull(
        serverMock
            .getClient()
            .configMaps()
            .inNamespace(TEST_NAMESPACE_NAME)
            .withName(GIT_USERDATA_CONFIGMAP_NAME)
            .get());
  }

  @Test
  public void doNothingWhenGitUserDataAndCheUserAreNull()
      throws InfrastructureException, ServerException, NotFoundException {
    // when
    when(userManager.getById(anyString())).thenThrow(new NotFoundException("not found"));
    configurator.configure(namespaceResolutionContext, TEST_NAMESPACE_NAME);

    // then - don't create the configmap
    var configMaps =
        serverMock.getClient().configMaps().inNamespace(TEST_NAMESPACE_NAME).list().getItems();
    Assert.assertEquals(configMaps.size(), 0);
  }

  @Test
  public void doNothingWhenSecretAlreadyExists()
      throws InfrastructureException, InterruptedException, ScmCommunicationException,
          ScmUnauthorizedException {
    // given
    when(gitUserDataFetcher.fetchGitUserData()).thenReturn(new GitUserData("gitUser", "gitEmail"));
    Map<String, String> annotations =
        ImmutableMap.of(
            "controller.devfile.io/mount-as",
            "subpath",
            "controller.devfile.io/mount-path",
            "/etc/",
            "already",
            "created");
    Map<String, String> labels =
        ImmutableMap.of(
            "controller.devfile.io/mount-to-devworkspace",
            "true",
            "controller.devfile.io/watch-configmap",
            "true",
            "already",
            "created");
    ConfigMap configMap =
        new ConfigMapBuilder()
            .withNewMetadata()
            .withName(GIT_USERDATA_CONFIGMAP_NAME)
            .withLabels(labels)
            .withAnnotations(annotations)
            .endMetadata()
            .build();
    configMap.setData(Collections.singletonMap("gitconfig", "empty"));
    serverMock.getClient().configMaps().inNamespace(TEST_NAMESPACE_NAME).create(configMap);

    // when
    configurator.configure(namespaceResolutionContext, TEST_NAMESPACE_NAME);

    // then - don't create the configmap
    Assert.assertEquals(serverMock.getLastRequest().getMethod(), "GET");
    var configMaps =
        serverMock.getClient().configMaps().inNamespace(TEST_NAMESPACE_NAME).list().getItems();
    Assert.assertEquals(configMaps.size(), 1);
    Assert.assertEquals(configMaps.get(0).getMetadata().getAnnotations().get("already"), "created");
  }

  @Test
  public void createUserdataConfigmapFromCheUserData()
      throws InfrastructureException, ServerException, NotFoundException, InterruptedException {
    // given
    User user = mock(User.class);
    when(user.getName()).thenReturn("test name");
    when(user.getEmail()).thenReturn("test@email.com");
    when(userManager.getById(anyString())).thenReturn(user);

    // when
    configurator.configure(namespaceResolutionContext, TEST_NAMESPACE_NAME);

    // then create a secret
    Assert.assertEquals(serverMock.getLastRequest().getMethod(), "POST");
    ConfigMap configMap =
        serverMock
            .getClient()
            .configMaps()
            .inNamespace(TEST_NAMESPACE_NAME)
            .withName(GIT_USERDATA_CONFIGMAP_NAME)
            .get();
    Assert.assertNotNull(configMap);
    Assert.assertTrue(configMap.getData().get("gitconfig").contains("test name"));
    Assert.assertTrue(configMap.getData().get("gitconfig").contains("test@email.com"));
  }
}
