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
package org.eclipse.che.workspace.infrastructure.kubernetes.authorization;

import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class KubernetesAuthorizationCheckerTest {
  @Test(dataProvider = "advancedAuthorizationData")
  public void advancedAuthorization(
      String testUserName, String allowedUsers, String disabledUsers, boolean expectedIsAuthorized)
      throws InfrastructureException {
    // give
    AuthorizationChecker authorizationChecker =
        new KubernetesAuthorizationCheckerImpl(allowedUsers, disabledUsers);

    // when
    boolean isAuthorized = authorizationChecker.isAuthorized(testUserName);

    // then
    Assert.assertEquals(isAuthorized, expectedIsAuthorized);
  }

  @DataProvider
  public static Object[][] advancedAuthorizationData() {
    return new Object[][] {
      {"user1", "", "", true},
      {"user1", "user1", "", true},
      {"user1", "user1", "user2", true},
      {"user1", "user1", "user1", false},
      {"user2", "user1", "", false},
      {"user2", "user1", "user2", false},
    };
  }
}
