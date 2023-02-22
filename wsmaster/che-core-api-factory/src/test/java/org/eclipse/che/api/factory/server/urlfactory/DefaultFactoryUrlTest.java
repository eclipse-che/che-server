/*
 * Copyright (c) 2012-2023 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.factory.server.urlfactory;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Optional;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/** Testing {@link DefaultFactoryUrl} */
@Listeners(MockitoTestNGListener.class)
public class DefaultFactoryUrlTest {
  @Test(dataProvider = "urlsProvider")
  public void shouldGetCredentials(String url, String credentials) {
    // given
    DefaultFactoryUrl factoryUrl = new DefaultFactoryUrl().withUrl(url);
    // when
    Optional<String> credentialsOptional = factoryUrl.getCredentials();
    // then
    assertTrue(credentialsOptional.isPresent());
    assertEquals(credentialsOptional.get(), credentials);
  }

  @DataProvider(name = "urlsProvider")
  private Object[][] urlsProvider() {
    return new Object[][] {
      {"https://username:password@hostname/path", "username:password"},
      {"https://token@hostname/path/user/repo/", "token:"},
      {"http://token@hostname/path/user/repo/", "token:"},
      {"https://token@dev.azure.com/user/repo/", "token:"},
      {
        "https://personal-access-token@raw.githubusercontent.com/user/repo/branch/.devfile.yaml",
        "personal-access-token:"
      },
      {"https://token@gitlub.com/user/repo/", "token:"},
      {"https://token@bitbucket.org/user/repo/", "token:"},
    };
  }

  @Test
  public void shouldGetEmptyCredentials() {
    // given
    DefaultFactoryUrl factoryUrl = new DefaultFactoryUrl().withUrl("https://hostname/path");
    // when
    Optional<String> credentialsOptional = factoryUrl.getCredentials();
    // then
    assertFalse(credentialsOptional.isPresent());
  }
}
