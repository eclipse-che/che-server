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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.user.server.PreferenceManager;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.api.user.server.model.impl.UserImpl;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.KubernetesClientFactory;
import org.eclipse.che.workspace.infrastructure.kubernetes.api.server.impls.KubernetesNamespaceMetaImpl;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespaceFactory;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class NamespaceProvisionerTest {

  @Mock private KubernetesNamespaceFactory namespaceFactory;
  @Mock private KubernetesClientFactory clientFactory;
  @Mock private UserManager userManager;
  @Mock private PreferenceManager preferenceManager;
  @InjectMocks private NamespaceProvisioner namespaceProvisioner;

  private static final String USER_ID = "user-id";
  private static final String USER_NAME = "user-name";
  private static final String USER_EMAIL = "user-email";
  private static final String USER_NAMESPACE = "user-namespace";

  private KubernetesServer kubernetesServer;

  @BeforeMethod
  public void setUp() throws InfrastructureException, NotFoundException, ServerException {
    kubernetesServer = new KubernetesServer(true, true);
    kubernetesServer.before();

    Map<String, String> preferences = new HashMap<>();
    preferences.put("preference-name", "preference");

    lenient().when(clientFactory.create()).thenReturn(kubernetesServer.getClient());
    lenient()
        .when(namespaceFactory.provision(any()))
        .thenReturn(new KubernetesNamespaceMetaImpl(USER_NAMESPACE, Collections.emptyMap()));
    lenient()
        .when(userManager.getById(USER_ID))
        .thenReturn(new UserImpl(USER_ID, USER_EMAIL, USER_NAME));
    lenient().when(namespaceFactory.evaluateNamespaceName(any())).thenReturn(USER_NAMESPACE);
    lenient().when(preferenceManager.find(USER_ID)).thenReturn(preferences);
  }

  @AfterMethod
  public void cleanUp() {
    kubernetesServer.after();
  }

  @Test
  public void shouldCreateSecretsOnNamespaceProvision() throws InfrastructureException {
    namespaceProvisioner.provision(new NamespaceResolutionContext(null, USER_ID, USER_NAME));
    List<Secret> createdSecrets =
        kubernetesServer.getClient().secrets().inNamespace(USER_NAMESPACE).list().getItems();
    assertEquals(createdSecrets.size(), 2);
    assertTrue(
        createdSecrets
            .stream()
            .anyMatch(secret -> secret.getMetadata().getName().equals("user-profile")));
    assertTrue(
        createdSecrets
            .stream()
            .anyMatch(secret -> secret.getMetadata().getName().equals("user-preferences")));
  }

  @Test
  public void shouldCreateOnlyProfileSecret() throws InfrastructureException, ServerException {
    when(preferenceManager.find(USER_ID)).thenReturn(Collections.emptyMap());
    namespaceProvisioner.provision(new NamespaceResolutionContext(null, USER_ID, USER_NAME));
    List<Secret> createdSecrets =
        kubernetesServer.getClient().secrets().inNamespace(USER_NAMESPACE).list().getItems();
    assertEquals(createdSecrets.size(), 1);
    assertEquals(createdSecrets.get(0).getMetadata().getName(), "user-profile");
  }

  @Test
  public void shouldCreateNoSecretOnException() throws InfrastructureException, NotFoundException, ServerException {
    when(userManager.getById(USER_ID)).thenThrow(new ServerException("Test server exception"));
    namespaceProvisioner.provision(new NamespaceResolutionContext(null, USER_ID, USER_NAME));
    assertTrue(kubernetesServer.getClient().secrets().inNamespace(USER_NAMESPACE).list().getItems().isEmpty());
    verifyNoInteractions(clientFactory);
  }

  @Test
  public void shouldNormalizePreferenceName() {
    assertEquals(namespaceProvisioner.normalizePreferenceName("codename:bond"), "codename-bond");
    assertEquals(namespaceProvisioner.normalizePreferenceName("some--:pref"), "some-pref");
    assertEquals(namespaceProvisioner.normalizePreferenceName("pref[name].sub"), "pref-name-.sub");
  }

  @Test
  public void shouldKeepPreferenceName() {
    assertEquals(namespaceProvisioner.normalizePreferenceName("codename.bond"), "codename.bond");
    assertEquals(namespaceProvisioner.normalizePreferenceName("pref_name"), "pref_name");
    assertEquals(
        namespaceProvisioner.normalizePreferenceName("some-name.over_rainbow"),
        "some-name.over_rainbow");
  }
}
