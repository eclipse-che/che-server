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
package org.eclipse.che.workspace.infrastructure.kubernetes.namespace.configurator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
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
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespaceFactory;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Tests for {@link UserPreferencesConfigurator}.
 *
 * @author Pavol Baran
 */
@Listeners(MockitoTestNGListener.class)
public class UserPreferencesConfiguratorTest {

  private static final String USER_ID = "user-id";
  private static final String USER_NAME = "user-name";
  private static final String USER_EMAIL = "user-email";
  private static final String USER_NAMESPACE = "user-namespace";

  @Mock private KubernetesNamespaceFactory namespaceFactory;
  @Mock private KubernetesClientFactory clientFactory;
  @Mock private UserManager userManager;
  @Mock private PreferenceManager preferenceManager;

  @InjectMocks private UserPreferencesConfigurator userPreferencesConfigurator;

  private KubernetesServer kubernetesServer;
  private NamespaceResolutionContext context;

  @BeforeMethod
  public void setUp() throws InfrastructureException, NotFoundException, ServerException {
    context = new NamespaceResolutionContext(null, USER_ID, USER_NAME);
    kubernetesServer = new KubernetesServer(true, true);
    kubernetesServer.before();

    Map<String, String> preferences = new HashMap<>();
    preferences.put("preference-name", "preference");

    lenient()
        .when(userManager.getById(USER_ID))
        .thenReturn(new UserImpl(USER_ID, USER_EMAIL, USER_NAME));
    lenient().when(namespaceFactory.evaluateNamespaceName(any())).thenReturn(USER_NAMESPACE);
    lenient().when(clientFactory.create()).thenReturn(kubernetesServer.getClient());
    lenient().when(preferenceManager.find(USER_ID)).thenReturn(preferences);
  }

  @AfterMethod
  public void cleanUp() {
    kubernetesServer.after();
  }

  @Test
  public void shouldCreatePreferencesSecret() throws InfrastructureException {
    userPreferencesConfigurator.configure(context);
    List<Secret> secrets =
        kubernetesServer.getClient().secrets().inNamespace(USER_NAMESPACE).list().getItems();
    assertEquals(secrets.size(), 1);
    assertEquals(secrets.get(0).getMetadata().getName(), "user-preferences");
  }

  @Test(
      expectedExceptions = InfrastructureException.class,
      expectedExceptionsMessageRegExp =
          "Preferences of user with id:" + USER_ID + " cannot be retrieved.")
  public void shouldNotCreateSecretOnException() throws ServerException, InfrastructureException {
    when(preferenceManager.find(USER_ID)).thenThrow(new ServerException("test exception"));
    userPreferencesConfigurator.configure(context);
    fail("InfrastructureException should have been thrown.");
  }

  @Test
  public void shouldNormalizePreferenceName() {
    assertEquals(
        userPreferencesConfigurator.normalizePreferenceName("codename:bond"), "codename-bond");
    assertEquals(userPreferencesConfigurator.normalizePreferenceName("some--:pref"), "some-pref");
    assertEquals(
        userPreferencesConfigurator.normalizePreferenceName("pref[name].sub"), "pref-name-.sub");
  }

  @Test
  public void shouldKeepPreferenceName() {
    assertEquals(
        userPreferencesConfigurator.normalizePreferenceName("codename.bond"), "codename.bond");
    assertEquals(userPreferencesConfigurator.normalizePreferenceName("pref_name"), "pref_name");
    assertEquals(
        userPreferencesConfigurator.normalizePreferenceName("some-name.over_rainbow"),
        "some-name.over_rainbow");
  }
}
