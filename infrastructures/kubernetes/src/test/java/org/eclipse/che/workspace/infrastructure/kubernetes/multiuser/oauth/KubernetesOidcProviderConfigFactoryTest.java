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
package org.eclipse.che.workspace.infrastructure.kubernetes.multiuser.oauth;

import static org.testng.Assert.assertEquals;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class KubernetesOidcProviderConfigFactoryTest {
  private static final String TEST_TOKEN = "touken";

  private Config defaultConfig;

  private KubernetesOidcProviderConfigFactory kubernetesOidcProviderConfigFactory =
      new KubernetesOidcProviderConfigFactory(null, null);

  @BeforeMethod
  public void setUp() {
    EnvironmentContext.reset();
    defaultConfig = new ConfigBuilder().build();
  }

  @Test
  public void getDefaultConfigWhenNoTokenSet() {
    Config resultConfig = kubernetesOidcProviderConfigFactory.buildConfig(defaultConfig, null);

    assertEquals(resultConfig, defaultConfig);
  }

  @Test
  public void getConfigWithTokenWhenTokenIsSet() {
    EnvironmentContext.getCurrent()
        .setSubject(new SubjectImpl("test_name", "test_id", TEST_TOKEN, false));

    Config resultConfig = kubernetesOidcProviderConfigFactory.buildConfig(defaultConfig, null);

    assertEquals(resultConfig.getOauthToken(), TEST_TOKEN);
  }
}
