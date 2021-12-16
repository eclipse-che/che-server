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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.User;
import io.fabric8.openshift.client.OpenShiftClient;
import java.util.Optional;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.api.user.server.model.impl.UserImpl;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.multiuser.api.authentication.commons.SessionStore;
import org.eclipse.che.multiuser.api.authentication.commons.token.RequestTokenExtractor;
import org.eclipse.che.multiuser.api.permission.server.PermissionChecker;
import org.eclipse.che.workspace.infrastructure.openshift.OpenShiftClientFactory;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class OpenshiftTokenInitializationFilterTest {
  @Mock private SessionStore sessionStore;
  @Mock private RequestTokenExtractor tokenExtractor;
  @Mock private UserManager userManager;
  @Mock private PermissionChecker permissionChecker;

  @Mock private OpenShiftClientFactory openShiftClientFactory;
  @Mock private OpenShiftClient openShiftClient;
  @Mock private User openshiftUser;
  @Mock private ObjectMeta openshiftUserMeta;

  private static final String TOKEN = "touken";
  private static final String USER_UID = "almost-certainly-unique-id";
  private static final String USERNAME = "test_username";

  private OpenshiftTokenInitializationFilter openshiftTokenInitializationFilter;

  @BeforeMethod
  public void setUp() throws InfrastructureException {
    openshiftTokenInitializationFilter =
        new OpenshiftTokenInitializationFilter(
            sessionStore, tokenExtractor, openShiftClientFactory, userManager, permissionChecker);
  }

  @Test
  public void getUserIdGetsCurrentUserWithAuthenticatedOCClient() {
    when(openShiftClientFactory.createAuthenticatedClient(TOKEN)).thenReturn(openShiftClient);
    when(openShiftClient.currentUser()).thenReturn(openshiftUser);
    when(openshiftUser.getMetadata()).thenReturn(openshiftUserMeta);
    when(openshiftUserMeta.getUid()).thenReturn(USER_UID);

    User u = openshiftTokenInitializationFilter.processToken(TOKEN).get();
    String userId = openshiftTokenInitializationFilter.getUserId(u);

    assertEquals(u, openshiftUser);
    assertEquals(userId, USER_UID);
    verify(openShiftClientFactory).createAuthenticatedClient(TOKEN);
    verify(openShiftClient).currentUser();
  }

  @Test
  public void shouldBeAbleToHandleKubeAdminUserWithoutUid()
      throws ServerException, ConflictException {
    String KUBE_ADMIN_USERNAME = "kube:admin";
    when(openShiftClientFactory.createAuthenticatedClient(TOKEN)).thenReturn(openShiftClient);
    when(openShiftClient.currentUser()).thenReturn(openshiftUser);
    when(openshiftUser.getMetadata()).thenReturn(openshiftUserMeta);
    when(openshiftUserMeta.getUid()).thenReturn(null);
    when(openshiftUserMeta.getName()).thenReturn(KUBE_ADMIN_USERNAME);
    when(userManager.getOrCreateUser(KUBE_ADMIN_USERNAME, KUBE_ADMIN_USERNAME))
        .thenReturn(
            new UserImpl(KUBE_ADMIN_USERNAME, KUBE_ADMIN_USERNAME + "@che", KUBE_ADMIN_USERNAME));

    User u = openshiftTokenInitializationFilter.processToken(TOKEN).get();
    Subject subject = openshiftTokenInitializationFilter.extractSubject(TOKEN, u);

    assertEquals(subject.getUserId(), KUBE_ADMIN_USERNAME);
    assertEquals(subject.getUserName(), KUBE_ADMIN_USERNAME);
  }

  @Test
  public void extractSubjectCreatesSubjectWithCurrentlyAuthenticatedUser()
      throws ServerException, ConflictException {
    when(openShiftClientFactory.createAuthenticatedClient(TOKEN)).thenReturn(openShiftClient);
    when(openShiftClient.currentUser()).thenReturn(openshiftUser);
    when(openshiftUser.getMetadata()).thenReturn(openshiftUserMeta);
    when(openshiftUserMeta.getUid()).thenReturn(USER_UID);
    when(openshiftUserMeta.getName()).thenReturn(USERNAME);
    when(userManager.getOrCreateUser(USER_UID, USERNAME))
        .thenReturn(new UserImpl(USER_UID, USERNAME + "@che", USERNAME));

    User u = openshiftTokenInitializationFilter.processToken(TOKEN).get();
    Subject subject = openshiftTokenInitializationFilter.extractSubject(TOKEN, u);

    assertEquals(u, openshiftUser);
    assertEquals(subject.getUserId(), USER_UID);
    assertEquals(subject.getUserName(), USERNAME);
  }

  @Test
  public void invalidTokenShouldBeHandledAsMissing() throws Exception {
    when(openShiftClientFactory.createAuthenticatedClient(TOKEN)).thenReturn(openShiftClient);
    when(openShiftClient.currentUser())
        .thenThrow(new KubernetesClientException("failah", 401, null));

    Optional<User> u = openshiftTokenInitializationFilter.processToken(TOKEN);
    assertTrue(u.isEmpty());
  }
}
