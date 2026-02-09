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
package org.eclipse.che.workspace.infrastructure.openshift.authorization;

import static org.mockito.Mockito.*;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMixedDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.mockwebserver.Context;
import io.fabric8.mockwebserver.MockWebServer;
import io.fabric8.mockwebserver.ServerRequest;
import io.fabric8.mockwebserver.ServerResponse;
import io.fabric8.openshift.api.model.Group;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.eclipse.che.workspace.infrastructure.kubernetes.CheServerKubernetesClientFactory;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class OpenShiftAuthorizationCheckerTest {

  @Mock private CheServerKubernetesClientFactory clientFactory;
  private KubernetesClient client;
  private KubernetesMockServer kubernetesMockServer;
  private static Subject user1 =
      new SubjectImpl("user1", Collections.emptyList(), "id", "token", false);
  private static Subject user2 =
      new SubjectImpl("user2", Collections.emptyList(), "id", "token", false);

  @BeforeMethod
  public void setUp() throws InfrastructureException {
    final Map<ServerRequest, Queue<ServerResponse>> responses = new HashMap<>();
    kubernetesMockServer =
        new KubernetesMockServer(
            new Context(),
            new MockWebServer(),
            responses,
            new KubernetesMixedDispatcher(responses),
            true);
    kubernetesMockServer.init();
    client = spy(kubernetesMockServer.createClient());
    lenient().when(clientFactory.create()).thenReturn(client);
  }

  @AfterMethod
  public void tearDown() {
    kubernetesMockServer.destroy();
  }

  @Test(dataProvider = "advancedAuthorizationData")
  public void advancedAuthorization(
      Subject subject,
      List<Group> groups,
      String allowedUsers,
      String allowedGroups,
      String deniedUsers,
      String deniedGroups,
      boolean expectedIsAuthorized)
      throws InfrastructureException {
    // give
    OpenShiftAuthorizationCheckerImpl authorizationChecker =
        new OpenShiftAuthorizationCheckerImpl(
            allowedUsers, allowedGroups, deniedUsers, deniedGroups, clientFactory);
    groups.forEach(group -> client.resources(Group.class).create(group));

    // when
    boolean isAuthorized = authorizationChecker.isAuthorized(subject);

    // then
    Assert.assertEquals(isAuthorized, expectedIsAuthorized);
  }

  @DataProvider
  public static Object[][] advancedAuthorizationData() {
    Group groupWithUser1 =
        new Group(
            "v1",
            "Group",
            new ObjectMetaBuilder().withName("groupWithUser1").build(),
            List.of("user1"));
    Group groupWithUser2 =
        new Group(
            "v1",
            "Group",
            new ObjectMetaBuilder().withName("groupWithUser2").build(),
            List.of("user2"));

    return new Object[][] {
      {user1, Collections.emptyList(), "", "", "", "", true},
      {user1, Collections.emptyList(), "user1", "", "", "", true},
      {user1, Collections.emptyList(), "user1", "", "user2", "", true},
      {user1, List.of(groupWithUser2), "user1", "", "", "groupWithUser2", true},
      {user1, List.of(groupWithUser1), "", "groupWithUser1", "", "", true},
      {user2, List.of(groupWithUser1), "user2", "groupWithUser1", "", "", true},
      {
        user1,
        List.of(groupWithUser1, groupWithUser2),
        "",
        "groupWithUser1",
        "",
        "groupWithUser2",
        true
      },
      {user1, Collections.emptyList(), "user1", "", "user1", "", false},
      {user2, Collections.emptyList(), "user1", "", "", "", false},
      {user2, Collections.emptyList(), "user1", "", "user2", "", false},
      {user2, List.of(groupWithUser1), "", "groupWithUser1", "", "", false},
      {user1, Collections.emptyList(), "", "", "user1", "", false},
      {user1, List.of(groupWithUser1), "", "", "", "groupWithUser1", false},
      {user1, List.of(groupWithUser1), "", "groupWithUser1", "", "groupWithUser1", false},
      {
        user2,
        List.of(groupWithUser1, groupWithUser2),
        "",
        "groupWithUser1",
        "",
        "groupWithUser2",
        false
      },
    };
  }
}
