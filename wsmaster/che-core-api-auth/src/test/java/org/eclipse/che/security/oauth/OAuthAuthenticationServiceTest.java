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
package org.eclipse.che.security.oauth;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static org.eclipse.che.security.oauth.OAuthCsrfStateValidator.NONCE_PARAM;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URLEncoder;
import java.util.List;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.factory.server.scm.AuthorisationRequestManager;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Covers the CSRF nonce check that guards the OAuth 2 callback. See {@link
 * OAuthCsrfStateValidator}.
 */
@Listeners(value = MockitoTestNGListener.class)
public class OAuthAuthenticationServiceTest {

  private static final String ALICE = "alice-id";
  private static final String MALLORY = "mallory-id";

  @Mock private OAuthAPI oAuthAPI;
  @Mock private AuthorisationRequestManager authorisationRequestManager;

  @Spy
  private OAuthCsrfStateValidator csrfStateValidator =
      new OAuthCsrfStateValidator("https://eclipse.che/api");

  @InjectMocks private OAuthAuthenticationService service;

  @BeforeMethod
  public void setUp() {
    setSubject(ALICE);
  }

  @AfterMethod
  public void tearDown() {
    EnvironmentContext.reset();
  }

  @Test
  public void shouldProcessACallbackOfAFlowTheUserStarted() throws Exception {
    String nonce = csrfStateValidator.issueNonce();
    givenRequest(stateWithNonce(nonce));
    when(oAuthAPI.callback(any(UriInfo.class), anyList())).thenReturn(Response.ok().build());

    service.callback(emptyList(), requestWithStateCookie("github", nonce));

    verify(authorisationRequestManager).callback(any(UriInfo.class), anyList());
    verify(oAuthAPI).callback(any(UriInfo.class), anyList());
  }

  @Test
  public void shouldExpireTheStateCookieOfAProcessedCallback() throws Exception {
    String nonce = csrfStateValidator.issueNonce();
    givenRequest(stateWithNonce(nonce));
    when(oAuthAPI.callback(any(UriInfo.class), anyList())).thenReturn(Response.ok().build());

    Response response = service.callback(emptyList(), requestWithStateCookie("github", nonce));

    // a callback URL cannot be replayed once the browser has dropped the cookie it needs
    String setCookie = response.getHeaderString(HttpHeaders.SET_COOKIE);
    assertTrue(setCookie.contains("Max-Age=0"), "Unexpected Set-Cookie header: " + setCookie);
  }

  @Test
  public void shouldRejectAForgedCallback() throws Exception {
    // An attacker pre-computes a callback URL carrying a code issued to their own SCM account and
    // has a logged-in victim open it. Without a nonce in the state this stored the attacker's SCM
    // token under the victim's Che identity (CWE-352). The attacker cannot write a cookie for the
    // Che host, so the browser of the victim presents none.
    givenRequest("oauth_provider=github&redirect_after_login=https://che/dashboard");
    HttpServletRequest request = mock(HttpServletRequest.class);

    assertThrows(ForbiddenException.class, () -> service.callback(emptyList(), request));

    verifyNothingWasActedOn();
  }

  @Test
  public void shouldRejectACallbackCarryingANonceOfAnotherUsersFlow() throws Exception {
    // The attacker is a user of the same Che server, so they can start a flow of their own and
    // hand the victim both its callback URL and the cookie value it needs.
    setSubject(MALLORY);
    String nonce = csrfStateValidator.issueNonce();
    HttpServletRequest request = requestWithStateCookie("github", nonce);
    setSubject(ALICE);
    givenRequest(stateWithNonce(nonce));

    assertThrows(ForbiddenException.class, () -> service.callback(emptyList(), request));

    verifyNothingWasActedOn();
  }

  @Test
  public void shouldRejectADeniedCallbackOfAFlowTheUserDidNotStart() throws Exception {
    // access_denied makes the server remember not to ask this user for this provider again, so it
    // has to be out of reach of a forged callback as well
    givenRequest("oauth_provider=github");
    HttpServletRequest request = mock(HttpServletRequest.class);

    assertThrows(
        ForbiddenException.class, () -> service.callback(List.of("access_denied"), request));

    verifyNothingWasActedOn();
  }

  // --- helpers ---------------------------------------------------------------------------------

  private void verifyNothingWasActedOn() throws Exception {
    verify(authorisationRequestManager, never()).callback(any(UriInfo.class), anyList());
    verify(oAuthAPI, never()).callback(any(UriInfo.class), anyList());
  }

  private static String stateWithNonce(String nonce) {
    return "oauth_provider=github&" + NONCE_PARAM + "=" + nonce;
  }

  /** A callback request carrying the state cookie the current user would have been handed. */
  private HttpServletRequest requestWithStateCookie(String oauthProvider, String nonce) {
    String pair = csrfStateValidator.stateCookie(oauthProvider, nonce).split(";", 2)[0];
    int equals = pair.indexOf('=');
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getCookies())
        .thenReturn(
            new Cookie[] {new Cookie(pair.substring(0, equals), pair.substring(equals + 1))});
    return request;
  }

  /** Points the service at a callback request carrying {@code state}. */
  private void givenRequest(String state) throws Exception {
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getRequestUri())
        .thenReturn(
            URI.create(
                "http://eclipse.che/oauth/callback?code=auth-code&state="
                    + URLEncoder.encode(state, UTF_8)));
    Field field = OAuthAuthenticationService.class.getDeclaredField("uriInfo");
    field.setAccessible(true);
    field.set(service, uriInfo);
  }

  private static void setSubject(String userId) {
    EnvironmentContext context = new EnvironmentContext();
    context.setSubject(new SubjectImpl(userId, emptyList(), userId, "token", false));
    EnvironmentContext.setCurrent(context);
  }
}
