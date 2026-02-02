/*
 * Copyright (c) 2012-2026 Red Hat, Inc.
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

import static org.mockito.Mockito.*;

import java.util.*;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class KubernetesAuthorizationCheckerTest {

  @Mock private CheServerKubernetesClientFactory clientFactory;
  private static Subject user1 =
      new SubjectImpl("user1", Arrays.asList("group1"), "id", "token", false);

  @Test(dataProvider = "advancedAuthorizationData")
  public void advancedAuthorization(
      Subject subject,
      String allowedUsers,
      String allowedGroups,
      String deniedUsers,
      String deniedGroups,
      boolean expectedIsAuthorized)
      throws InfrastructureException {
    // give
    KubernetesOIDCAuthorizationCheckerImpl authorizationChecker =
        new KubernetesOIDCAuthorizationCheckerImpl(
            allowedUsers, allowedGroups, deniedUsers, deniedGroups);

    // when
    boolean isAuthorized = authorizationChecker.isAuthorized(subject);

    // then
    Assert.assertEquals(isAuthorized, expectedIsAuthorized);
  }

  @DataProvider
  public static Object[][] advancedAuthorizationData() {
    return new Object[][] {
      {user1, "", "", "", "", true},
      {user1, "", "", "", "group1", false},
      {user1, "", "", "", "group2", true},
      {user1, "", "", "user1", "", false},
      {user1, "", "", "user1", "group1", false},
      {user1, "", "", "user1", "group2", false},
      {user1, "", "", "user2", "", true},
      {user1, "", "", "user2", "group1", false},
      {user1, "", "", "user2", "group2", true},
      {user1, "", "group1", "", "", true},
      {user1, "", "group1", "", "group1", false},
      {user1, "", "group1", "", "group2", true},
      {user1, "", "group1", "user1", "", false},
      {user1, "", "group1", "user1", "group1", false},
      {user1, "", "group1", "user1", "group2", false},
      {user1, "", "group1", "user2", "", true},
      {user1, "", "group1", "user2", "group1", false},
      {user1, "", "group1", "user2", "group2", true},
      {user1, "", "group2", "", "", false},
      {user1, "", "group2", "", "group1", false},
      {user1, "", "group2", "", "group2", false},
      {user1, "", "group2", "user1", "", false},
      {user1, "", "group2", "user1", "group1", false},
      {user1, "", "group2", "user1", "group2", false},
      {user1, "", "group2", "user2", "", false},
      {user1, "", "group2", "user2", "group1", false},
      {user1, "", "group2", "user2", "group2", false},
      {user1, "user1", "", "", "", true},
      {user1, "user1", "", "", "group1", false},
      {user1, "user1", "", "", "group2", true},
      {user1, "user1", "", "user1", "", false},
      {user1, "user1", "", "user1", "group1", false},
      {user1, "user1", "", "user1", "group2", false},
      {user1, "user1", "", "user2", "", true},
      {user1, "user1", "", "user2", "group1", false},
      {user1, "user1", "", "user2", "group2", true},
      {user1, "user1", "group1", "", "", true},
      {user1, "user1", "group1", "", "group1", false},
      {user1, "user1", "group1", "", "group2", true},
      {user1, "user1", "group1", "user1", "", false},
      {user1, "user1", "group1", "user1", "group1", false},
      {user1, "user1", "group1", "user1", "group2", false},
      {user1, "user1", "group1", "user2", "", true},
      {user1, "user1", "group1", "user2", "group1", false},
      {user1, "user1", "group1", "user2", "group2", true},
      {user1, "user1", "group2", "", "", true},
      {user1, "user1", "group2", "", "group1", false},
      {user1, "user1", "group2", "", "group2", true},
      {user1, "user1", "group2", "user1", "", false},
      {user1, "user1", "group2", "user1", "group1", false},
      {user1, "user1", "group2", "user1", "group2", false},
      {user1, "user1", "group2", "user2", "", true},
      {user1, "user1", "group2", "user2", "group1", false},
      {user1, "user1", "group2", "user2", "group2", true},
      {user1, "user2", "", "", "", false},
      {user1, "user2", "", "", "group1", false},
      {user1, "user2", "", "", "group2", false},
      {user1, "user2", "", "user1", "", false},
      {user1, "user2", "", "user1", "group1", false},
      {user1, "user2", "", "user1", "group2", false},
      {user1, "user2", "", "user2", "", false},
      {user1, "user2", "", "user2", "group1", false},
      {user1, "user2", "", "user2", "group2", false},
      {user1, "user2", "group1", "", "", true},
      {user1, "user2", "group1", "", "group1", false},
      {user1, "user2", "group1", "", "group2", true},
      {user1, "user2", "group1", "user1", "", false},
      {user1, "user2", "group1", "user1", "group1", false},
      {user1, "user2", "group1", "user1", "group2", false},
      {user1, "user2", "group1", "user2", "", true},
      {user1, "user2", "group1", "user2", "group1", false},
      {user1, "user2", "group1", "user2", "group2", true},
      {user1, "user2", "group2", "", "", false},
      {user1, "user2", "group2", "", "group1", false},
      {user1, "user2", "group2", "", "group2", false},
      {user1, "user2", "group2", "user1", "", false},
      {user1, "user2", "group2", "user1", "group1", false},
      {user1, "user2", "group2", "user1", "group2", false},
      {user1, "user2", "group2", "user2", "", false},
      {user1, "user2", "group2", "user2", "group1", false},
      {user1, "user2", "group2", "user2", "group2", false},
    };
  }
}
