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
import static org.eclipse.che.security.oauth.OAuthCsrfStateStore.NONCE_PARAM;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URLEncoder;
import java.util.List;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.factory.server.scm.AuthorisationRequestManager;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
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
 * Covers the CSRF nonce check that guards the OAuth 2 callback. See {@link OAuthCsrfStateStore}.
 */
@Listeners(value = MockitoTestNGListener.class)
public class OAuthAuthenticationServiceTest {

  private static final String ALICE = "alice-id";
  private static final String MALLORY = "mallory-id";

  @Mock private OAuthAPI oAuthAPI;
  @Mock private AuthorisationRequestManager authorisationRequestManager;
  @Spy private OAuthCsrfStateStore csrfStateStore = new OAuthCsrfStateStore();

  @InjectMocks private OAuthAuthenticationService service;

  @BeforeMethod
  public void setUp() {
    setSubject(new SubjectImpl("alice", emptyList(), ALICE, "token", false));
  }

  @AfterMethod
  public void tearDown() {
    EnvironmentContext.reset();
  }

  @Test
  public void shouldProcessACallbackOfAFlowTheUserStarted() throws Exception {
    givenRequest(stateWithNonce(csrfStateStore.issue(ALICE)));
    when(oAuthAPI.callback(any(UriInfo.class), anyList())).thenReturn(Response.ok().build());

    service.callback(emptyList());

    verify(authorisationRequestManager).callback(any(UriInfo.class), anyList());
    verify(oAuthAPI).callback(any(UriInfo.class), anyList());
  }

  @Test
  public void shouldRejectAForgedCallback() throws Exception {
    // An attacker pre-computes a callback URL carrying a code issued to their own SCM account and
    // has a logged-in victim open it. Without a nonce in the state this stored the attacker's SCM
    // token under the victim's Che identity (CWE-352).
    givenRequest("oauth_provider=github&redirect_after_login=https://che/dashboard");

    assertThrows(ForbiddenException.class, () -> service.callback(emptyList()));

    verifyNothingWasActedOn();
  }

  @Test
  public void shouldRejectACallbackCarryingANonceOfAnotherUsersFlow() throws Exception {
    // The attacker is a user of the same Che server, so they can obtain a valid nonce of their own.
    givenRequest(stateWithNonce(csrfStateStore.issue(MALLORY)));

    assertThrows(ForbiddenException.class, () -> service.callback(emptyList()));

    verifyNothingWasActedOn();
  }

  @Test
  public void shouldRejectAReplayedCallback() throws Exception {
    givenRequest(stateWithNonce(csrfStateStore.issue(ALICE)));
    when(oAuthAPI.callback(any(UriInfo.class), anyList())).thenReturn(Response.ok().build());
    service.callback(emptyList());

    assertThrows(ForbiddenException.class, () -> service.callback(emptyList()));

    // the one legitimate call went through, the replay did not add a second
    verify(oAuthAPI).callback(any(UriInfo.class), anyList());
  }

  @Test
  public void shouldRejectADeniedCallbackOfAFlowTheUserDidNotStart() throws Exception {
    // access_denied makes the server remember not to ask this user for this provider again, so it
    // has to be out of reach of a forged callback as well
    givenRequest("oauth_provider=github");

    assertThrows(ForbiddenException.class, () -> service.callback(List.of("access_denied")));

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

  private static void setSubject(Subject subject) {
    EnvironmentContext context = new EnvironmentContext();
    context.setSubject(subject);
    EnvironmentContext.setCurrent(context);
  }
}
