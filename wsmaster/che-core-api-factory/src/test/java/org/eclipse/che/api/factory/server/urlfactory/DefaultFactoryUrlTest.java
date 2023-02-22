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
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/** Testing {@link DefaultFactoryUrl} */
@Listeners(MockitoTestNGListener.class)
public class DefaultFactoryUrlTest {
  @Test
  public void shouldCredentials() {
    // given
    DefaultFactoryUrl factoryUrl =
        new DefaultFactoryUrl().withUrl("https://username:password@hostname/path");
    // when
    Optional<String> credentialsOptional = factoryUrl.getCredentials();
    // then
    assertTrue(credentialsOptional.isPresent());
    assertEquals(credentialsOptional.get(), "username:password");
  }

  @Test
  public void shouldCredentialsWithoutPassword() {
    // given
    DefaultFactoryUrl factoryUrl =
        new DefaultFactoryUrl().withUrl("https://username@hostname/path");
    // when
    Optional<String> credentialsOptional = factoryUrl.getCredentials();
    // then
    assertTrue(credentialsOptional.isPresent());
    assertEquals(credentialsOptional.get(), "username:");
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
