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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.testng.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.user.server.PreferenceManager;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.NamespaceResolutionContext;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.KubernetesNamespaceFactory;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
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
  @Mock private PreferenceManager preferenceManager;

  @InjectMocks private UserPreferencesConfigurator userPreferencesConfigurator;

  private NamespaceResolutionContext context;

  @BeforeMethod
  public void setUp() throws InfrastructureException, NotFoundException, ServerException {
    context = new NamespaceResolutionContext(null, USER_ID, USER_NAME);

    Map<String, String> preferences = new HashMap<>();
    preferences.put("preference-name", "preference");

    lenient().when(namespaceFactory.evaluateNamespaceName(any())).thenReturn(USER_NAMESPACE);
    lenient().when(preferenceManager.find(USER_ID)).thenReturn(preferences);
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
