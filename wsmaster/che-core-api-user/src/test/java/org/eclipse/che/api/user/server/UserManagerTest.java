/*
 * Copyright (c) 2012-2024 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.user.server;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.Arrays;
import java.util.Collections;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.Page;
import org.eclipse.che.api.core.model.user.User;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.user.server.model.impl.UserImpl;
import org.eclipse.che.api.user.server.spi.PreferenceDao;
import org.eclipse.che.api.user.server.spi.ProfileDao;
import org.eclipse.che.api.user.server.spi.UserDao;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Ignore;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Tests for {@link UserManager}.
 *
 * @author Max Shaposhnik (mshaposhnik@codenvy.com)
 * @author Yevhenii Voevodin
 */
@Listeners(MockitoTestNGListener.class)
public class UserManagerTest {

  @Mock private UserDao userDao;
  @Mock private ProfileDao profileDao;
  @Mock private PreferenceDao preferencesDao;
  @Mock private EventService eventService;

  private UserManager manager;

  @BeforeMethod
  public void setUp() {
    manager =
        new UserManager(
            userDao, profileDao, preferencesDao, eventService, new String[] {"reserved"});
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void shouldThrowNpeWhenUpdatingUserWithNullEntity() throws Exception {
    manager.update(null);
  }

  @Test
  public void shouldUpdateUser() throws Exception {
    final UserImpl user =
        new UserImpl(
            "identifier", "test@email.com", "testName", "password", Collections.emptyList());
    UserImpl user2 = new UserImpl(user);
    user2.setName("testName2");
    manager.update(user2);

    verify(userDao).update(new UserImpl(user2));
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void shouldThrownNpeWhenTryingToGetUserByNullId() throws Exception {
    manager.getById(null);
  }

  @Test
  public void shouldGetUserById() throws Exception {
    final User user =
        new UserImpl(
            "identifier",
            "test@email.com",
            "testName",
            "password",
            Collections.singletonList("alias"));
    when(manager.getById(user.getId())).thenReturn(user);

    assertEquals(manager.getById(user.getId()), user);
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void shouldThrownNpeWhenTryingToGetUserByNullAlias() throws Exception {
    manager.getByAlias(null);
  }

  @Test
  public void shouldGetUserByAlias() throws Exception {
    final User user =
        new UserImpl(
            "identifier",
            "test@email.com",
            "testName",
            "password",
            Collections.singletonList("alias"));
    when(manager.getByAlias("alias")).thenReturn(user);

    assertEquals(manager.getByAlias("alias"), user);
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void shouldThrownNpeWhenTryingToGetUserByNullName() throws Exception {
    manager.getByName(null);
  }

  @Test
  public void shouldGetUserByName() throws Exception {
    final User user =
        new UserImpl(
            "identifier",
            "test@email.com",
            "testName",
            "password",
            Collections.singletonList("alias"));
    when(manager.getByName(user.getName())).thenReturn(user);

    assertEquals(manager.getByName(user.getName()), user);
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void shouldThrownNpeWhenTryingToGetUserWithNullEmail() throws Exception {
    manager.getByEmail(null);
  }

  @Test
  public void shouldGetUserByEmail() throws Exception {
    final User user =
        new UserImpl(
            "identifier",
            "test@email.com",
            "testName",
            "password",
            Collections.singletonList("alias"));
    when(manager.getByEmail(user.getEmail())).thenReturn(user);

    assertEquals(manager.getByEmail(user.getEmail()), user);
  }

  @Test
  public void shouldGetTotalUserCount() throws Exception {
    when(userDao.getTotalCount()).thenReturn(5L);

    assertEquals(manager.getTotalCount(), 5);
    verify(userDao).getTotalCount();
  }

  @Test
  public void shouldGetAllUsers() throws Exception {
    final Page users =
        new Page(
            Arrays.asList(
                new UserImpl(
                    "identifier1",
                    "test1@email.com",
                    "testName1",
                    "password",
                    Collections.singletonList("alias1")),
                new UserImpl(
                    "identifier2",
                    "test2@email.com",
                    "testName2",
                    "password",
                    Collections.singletonList("alias2")),
                new UserImpl(
                    "identifier3",
                    "test3@email.com",
                    "testName3",
                    "password",
                    Collections.singletonList("alias3"))),
            0,
            30,
            3);
    when(userDao.getAll(30, 0)).thenReturn(users);

    assertEquals(manager.getAll(30, 0), users);
    verify(userDao).getAll(30, 0);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void shouldThrowIllegalArgumentExceptionsWhenGetAllUsersWithNegativeMaxItems()
      throws Exception {
    manager.getAll(-5, 0);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void shouldThrowIllegalArgumentExceptionsWhenGetAllUsersWithNegativeSkipCount()
      throws Exception {
    manager.getAll(30, -11);
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void shouldThrowNpeWhenRemovingUserByNullId() throws Exception {
    manager.remove(null);
  }

  @Test
  public void shouldRemoveUser() throws Exception {
    manager.remove("user123");

    verify(userDao).remove("user123");
  }

  @Test(expectedExceptions = ConflictException.class)
  public void shouldThrowConflictExceptionOnCreationIfUserNameIsReserved() throws Exception {
    final User user = new UserImpl("id", "test@email.com", "reserved");

    manager.create(user, false);
  }

  @Test
  public void shouldBeAbleToGetOrCreate_existed() throws Exception {
    // when
    User actual = manager.getOrCreateUser("identifier", "testName@che", "testName");

    // then
    assertNotNull(actual);
    assertEquals(actual.getEmail(), "testName@che");
    assertEquals(actual.getId(), "identifier");
    assertEquals(actual.getName(), "testName");
  }

  @Test
  public void shouldBeAbleToGetOrCreate_nonexisted() throws Exception {
    // when
    User actual = manager.getOrCreateUser("identifier", "testName@che", "testName");
    // then
    assertNotNull(actual);
    assertEquals(actual.getEmail(), "testName@che");
    assertEquals(actual.getId(), "identifier");
    assertEquals(actual.getName(), "testName");
  }

  @Ignore
  @Test
  public void shouldBeAbleToGetOrCreateWithoutEmail_existed() throws Exception {
    // given
    final User user =
        new UserImpl(
            "identifier",
            "test@email.com",
            "testName",
            "password",
            Collections.singletonList("alias"));
    when(manager.getById(user.getId())).thenReturn(user);

    // when
    User actual = manager.getOrCreateUser("identifier", "testName");

    // then
    assertNotNull(actual);
    assertEquals(actual.getEmail(), "test@email.com");
    assertEquals(actual.getId(), "identifier");
    assertEquals(actual.getName(), "testName");
  }

  @Ignore
  @Test
  public void shouldBeAbleToGetOrCreateWithoutEmail_nonexisted() throws Exception {
    // given
    when(manager.getById("identifier")).thenThrow(NotFoundException.class);

    // when
    User actual = manager.getOrCreateUser("identifier", "testName");
    // then
    assertNotNull(actual);
    assertEquals(actual.getEmail(), "testName@che");
    assertEquals(actual.getId(), "identifier");
    assertEquals(actual.getName(), "testName");
  }
}
