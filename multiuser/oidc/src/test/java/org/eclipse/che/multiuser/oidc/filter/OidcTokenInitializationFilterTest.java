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
package org.eclipse.che.multiuser.oidc.filter;

import static org.eclipse.che.multiuser.oidc.filter.OidcTokenInitializationFilter.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import java.util.Arrays;
import java.util.List;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ServerException;
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
  private final String usernameClaim = "userClaim";
  private final String usernamePrefix = "oidc-user:";
  private final String groupsClaim = "groupClaim";
  private final String groupPrefix = "oidc-group:";
  private final String emailClaim = "emailClaim";
  private final String TEST_USERNAME = "jondoe";
  private final String TEST_USER_EMAIL = "jon@doe";
  private final String TEST_USERID = "jon1337";
  private final List<String> TEST_GROUPS = Arrays.asList("group1", "group2");
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
            usernameClaim,
            usernamePrefix,
            groupsClaim,
            groupPrefix,
            emailClaim);

    lenient().when(jwsClaims.getBody()).thenReturn(claims);
    lenient().when(claims.getSubject()).thenReturn(TEST_USERID);
    lenient().when(claims.get(emailClaim, String.class)).thenReturn(TEST_USER_EMAIL);
    lenient().when(claims.get(usernameClaim, String.class)).thenReturn(TEST_USERNAME);
    lenient().when(claims.get(groupsClaim)).thenReturn(TEST_GROUPS);
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
    var subject = tokenInitializationFilter.extractSubject(TEST_TOKEN, jwsClaims);

    assertEquals(subject.getUserId(), TEST_USERID);
    assertEquals(subject.getUserName(), usernamePrefix + TEST_USERNAME);
    assertEquals(subject.getGroups(), Arrays.asList("oidc-group:group1", "oidc-group:group2"));
    assertEquals(subject.getToken(), TEST_TOKEN);
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
            customUsernameClaim,
            usernamePrefix,
            groupsClaim,
            groupPrefix,
            emailClaim);
    when(claims.get(DEFAULT_USERNAME_CLAIM, String.class)).thenReturn(TEST_USERNAME);

    var subject = tokenInitializationFilter.extractSubject(TEST_TOKEN, jwsClaims);

    assertEquals(subject.getUserId(), TEST_USERID);
    assertEquals(subject.getUserName(), usernamePrefix + TEST_USERNAME);
    assertEquals(subject.getGroups(), Arrays.asList("oidc-group:group1", "oidc-group:group2"));
    assertEquals(subject.getToken(), TEST_TOKEN);
    verify(claims).get(DEFAULT_USERNAME_CLAIM, String.class);
    verify(claims, never()).get(usernameClaim, String.class);
  }

  @Test(dataProvider = "emailClaims")
  public void testDefaultEmailClaimWhenEmpty(String customEmailClaim)
      throws ServerException, ConflictException {
    tokenInitializationFilter =
        new OidcTokenInitializationFilter(
            permissionsChecker,
            jwtParser,
            sessionStore,
            tokenExtractor,
            usernameClaim,
            usernamePrefix,
            groupsClaim,
            groupPrefix,
            customEmailClaim);
    when(claims.get(DEFAULT_EMAIL_CLAIM, String.class)).thenReturn(TEST_USER_EMAIL);

    var subject = tokenInitializationFilter.extractSubject(TEST_TOKEN, jwsClaims);

    assertEquals(subject.getUserId(), TEST_USERID);
    assertEquals(subject.getUserName(), usernamePrefix + TEST_USERNAME);
    assertEquals(subject.getToken(), TEST_TOKEN);
    assertEquals(subject.getGroups(), Arrays.asList("oidc-group:group1", "oidc-group:group2"));
    verify(claims).get(DEFAULT_EMAIL_CLAIM, String.class);
    verify(claims, never()).get(emailClaim, String.class);
  }

  @Test(dataProvider = "groupsClaims")
  public void testDefaultGroupClaimWhenEmpty(String customGroupsClaim)
      throws ServerException, ConflictException {
    tokenInitializationFilter =
        new OidcTokenInitializationFilter(
            permissionsChecker,
            jwtParser,
            sessionStore,
            tokenExtractor,
            usernameClaim,
            usernamePrefix,
            customGroupsClaim,
            groupPrefix,
            emailClaim);
    when(claims.get(DEFAULT_GROUPS_CLAIM)).thenReturn(TEST_GROUPS);

    var subject = tokenInitializationFilter.extractSubject(TEST_TOKEN, jwsClaims);

    assertEquals(subject.getUserId(), TEST_USERID);
    assertEquals(subject.getUserName(), usernamePrefix + TEST_USERNAME);
    assertEquals(subject.getGroups(), Arrays.asList("oidc-group:group1", "oidc-group:group2"));
    assertEquals(subject.getToken(), TEST_TOKEN);
    verify(claims).get(DEFAULT_GROUPS_CLAIM);
    verify(claims, never()).get(groupsClaim);
  }

  @DataProvider
  public static Object[][] usernameClaims() {
    return new Object[][] {{""}, {null}};
  }

  @DataProvider
  public static Object[][] groupsClaims() {
    return new Object[][] {{""}, {null}};
  }

  @DataProvider
  public static Object[][] emailClaims() {
    return new Object[][] {{""}, {null}};
  }
}
