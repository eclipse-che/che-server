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
package org.eclipse.che.multiuser.keycloak.server;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;

import org.eclipse.che.account.api.AccountManager;
import org.eclipse.che.api.core.model.user.User;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.user.server.event.BeforeUserRemovedEvent;
import org.eclipse.che.api.user.server.event.PostUserPersistedEvent;
import org.eclipse.che.api.user.server.model.impl.UserImpl;
import org.eclipse.che.api.user.server.spi.PreferenceDao;
import org.eclipse.che.api.user.server.spi.ProfileDao;
import org.eclipse.che.api.user.server.spi.UserDao;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/** @author Mykhailo Kuznietsov */
@Listeners(value = {MockitoTestNGListener.class})
public class KeycloakUserManagerTest {

  @Mock private UserDao userDao;
  @Mock private ProfileDao profileDao;
  @Mock private PreferenceDao preferenceDao;
  @Mock private AccountManager accountManager;
  @Mock private EventService eventService;
  @Mock private PostUserPersistedEvent postUserPersistedEvent;
  @Mock private BeforeUserRemovedEvent beforeUserRemovedEvent;

  KeycloakUserManager keycloakUserManager;

  @BeforeMethod
  public void setUp() {
    keycloakUserManager =
        new KeycloakUserManager(
            userDao, profileDao, preferenceDao, accountManager, eventService, new String[] {});

    lenient()
        .when(eventService.publish(any()))
        .thenAnswer(
            invocationOnMock -> {
              Object arg = invocationOnMock.getArguments()[0];
              if (arg instanceof BeforeUserRemovedEvent) {
                return beforeUserRemovedEvent;
              } else {
                return postUserPersistedEvent;
              }
            });
  }

  @Test
  public void shouldReturnExistingUser() throws Exception {
    UserImpl userImpl = new UserImpl("id", "user@mail.com", "name");
    when(userDao.getById(eq("id"))).thenReturn(userImpl);

    User user = keycloakUserManager.getOrCreateUser("id", "user@mail.com", "name");

    verify(userDao).getById("id");
    assertEquals("id", user.getId());
    assertEquals("user@mail.com", user.getEmail());
    assertEquals("name", user.getName());
  }

  @Test
  public void shouldReturnUserAndUpdateHisEmail() throws Exception {
    // given
    ArgumentCaptor<UserImpl> captor = ArgumentCaptor.forClass(UserImpl.class);
    UserImpl userImpl = new UserImpl("id", "user@mail.com", "name");
    when(userDao.getById(eq("id"))).thenReturn(userImpl);

    // when
    User user = keycloakUserManager.getOrCreateUser("id", "new@mail.com", "name");

    // then
    verify(userDao, times(2)).getById("id");
    verify(userDao).update(captor.capture());
    assertEquals("id", captor.getValue().getId());
    assertEquals("new@mail.com", captor.getValue().getEmail());
    assertEquals("name", captor.getValue().getName());

    assertEquals("id", user.getId());
    assertEquals("new@mail.com", user.getEmail());
    assertEquals("name", user.getName());
  }
}
