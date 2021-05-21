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
package org.eclipse.che.workspace.infrastructure.openshift.multiuser.oauth;

import static org.testng.Assert.assertEquals;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import org.eclipse.che.workspace.infrastructure.kubernetes.multiuser.oauth.KubernetesOidcProviderConfigFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class OpenshiftProviderConfigFactoryTest {
  private static final String TEST_TOKEN = "touken";

  private Config defaultConfig;

  private KubernetesOidcProviderConfigFactory openshiftProviderConfigFactory =
      new KubernetesOidcProviderConfigFactory();

  @BeforeMethod
  public void setUp() {
    defaultConfig = new ConfigBuilder().build();
  }

  @Test
  public void getDefaultConfigWhenNoTokenSet() {
    Config resultConfig = openshiftProviderConfigFactory.buildConfig(defaultConfig, null);

    assertEquals(resultConfig, defaultConfig);
  }

  // TODO: test set token in EnvironmentContext
}
