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
package org.eclipse.che.multiuser.oidc.filter;

import static org.eclipse.che.multiuser.oidc.filter.OidcTokenInitializationFilter.DEFAULT_USERNAME_CLAIM;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.user.User;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.multiuser.api.authentication.commons.SessionStore;
import org.eclipse.che.multiuser.api.authentication.commons.token.RequestTokenExtractor;
import org.eclipse.che.multiuser.api.permission.server.PermissionChecker;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class OidcTokenInitializationFilterTest {

  @Mock private PermissionChecker permissionsChecker;
  @Mock private JwtParser jwtParser;
  @Mock private SessionStore sessionStore;
  @Mock private RequestTokenExtractor tokenExtractor;
  @Mock private UserManager userManager;
  private final String usernameClaim = "blabolClaim";
  private final String TEST_USERNAME = "jondoe";
  private final String TEST_USER_EMAIL = "jon@doe";
  private final String TEST_USERID = "jon1337";

  private final String TEST_TOKEN = "abcToken";

  @Mock private Jws<Claims> jwsClaims;
  @Mock private Claims claims;

  OidcTokenInitializationFilter tokenInitializationFilter;

  @BeforeMethod
  public void setUp() {
    tokenInitializationFilter =
        new OidcTokenInitializationFilter(
            permissionsChecker,
            jwtParser,
            sessionStore,
            tokenExtractor,
            userManager,
            usernameClaim);

    lenient().when(jwsClaims.getBody()).thenReturn(claims);
    lenient().when(claims.getSubject()).thenReturn(TEST_USERID);
    lenient().when(claims.get("email", String.class)).thenReturn(TEST_USER_EMAIL);
    lenient().when(claims.get(usernameClaim, String.class)).thenReturn(TEST_USERNAME);
  }

  @Test
  public void testProcessToken() {
    when(jwtParser.parseClaimsJws(TEST_TOKEN)).thenReturn(jwsClaims);

    var returnedClaims = tokenInitializationFilter.processToken(TEST_TOKEN).get();

    assertEquals(returnedClaims, jwsClaims);
    verify(jwtParser).parseClaimsJws(TEST_TOKEN);
  }

  @Test
  public void testProcessEmptyToken() {
    var returnedClaims = tokenInitializationFilter.processToken("");

    assertTrue(returnedClaims.isEmpty());
  }

  @Test
  public void testGetUserId() {
    var userId = tokenInitializationFilter.getUserId(jwsClaims);

    assertEquals(userId, TEST_USERID);
  }

  @Test
  public void testExtractSubject() throws ServerException, ConflictException {
    User createdUser = mock(User.class);
    when(createdUser.getId()).thenReturn(TEST_USERID);
    when(createdUser.getName()).thenReturn(TEST_USERNAME);
    when(userManager.getOrCreateUser(TEST_USERID, TEST_USER_EMAIL, TEST_USERNAME))
        .thenReturn(createdUser);

    var subject = tokenInitializationFilter.extractSubject(TEST_TOKEN, jwsClaims);

    assertEquals(subject.getUserId(), TEST_USERID);
    assertEquals(subject.getUserName(), TEST_USERNAME);
    assertEquals(subject.getToken(), TEST_TOKEN);
    verify(userManager).getOrCreateUser(TEST_USERID, TEST_USER_EMAIL, TEST_USERNAME);
  }

  @Test(dataProvider = "usernameClaims")
  public void testDefaultUsernameClaimWhenEmpty(String customUsernameClaim)
      throws ServerException, ConflictException {
    tokenInitializationFilter =
        new OidcTokenInitializationFilter(
            permissionsChecker,
            jwtParser,
            sessionStore,
            tokenExtractor,
            userManager,
            customUsernameClaim);
    User createdUser = mock(User.class);
    when(createdUser.getId()).thenReturn(TEST_USERID);
    when(createdUser.getName()).thenReturn(TEST_USERNAME);
    when(userManager.getOrCreateUser(TEST_USERID, TEST_USER_EMAIL, TEST_USERNAME))
        .thenReturn(createdUser);
    when(claims.get(DEFAULT_USERNAME_CLAIM, String.class)).thenReturn(TEST_USERNAME);

    var subject = tokenInitializationFilter.extractSubject(TEST_TOKEN, jwsClaims);

    assertEquals(subject.getUserId(), TEST_USERID);
    assertEquals(subject.getUserName(), TEST_USERNAME);
    assertEquals(subject.getToken(), TEST_TOKEN);
    verify(userManager).getOrCreateUser(TEST_USERID, TEST_USER_EMAIL, TEST_USERNAME);
    verify(claims).get(DEFAULT_USERNAME_CLAIM, String.class);
    verify(claims, never()).get(usernameClaim, String.class);
  }

  @DataProvider
  public static Object[][] usernameClaims() {
    return new Object[][] {{""}, {null}};
  }
}
