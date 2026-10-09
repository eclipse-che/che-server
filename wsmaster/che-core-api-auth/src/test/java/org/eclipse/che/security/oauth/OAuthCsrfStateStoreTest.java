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

import static java.util.Collections.emptyList;
import static org.eclipse.che.security.oauth.OAuthCsrfStateStore.NONCE_PARAM;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class OAuthCsrfStateStoreTest {

  private static final String ALICE = "alice-id";
  private static final String MALLORY = "mallory-id";

  private OAuthCsrfStateStore store;

  @BeforeMethod
  public void setUp() {
    store = new OAuthCsrfStateStore();
    setSubject(new SubjectImpl("alice", emptyList(), ALICE, "token", false));
  }

  @AfterMethod
  public void tearDown() {
    EnvironmentContext.reset();
  }

  // --- issue / consume -------------------------------------------------------------------------

  @Test
  public void shouldConsumeAnIssuedNonce() {
    assertTrue(store.consume(store.issue(ALICE), ALICE));
  }

  @Test
  public void shouldConsumeANonceOnlyOnce() {
    String nonce = store.issue(ALICE);

    assertTrue(store.consume(nonce, ALICE));
    // a callback URL that was already used must not work a second time
    assertFalse(store.consume(nonce, ALICE));
  }

  @Test
  public void shouldRejectANonceIssuedToAnotherUser() {
    // the attacker starts a flow of their own to obtain a valid nonce
    String nonce = store.issue(MALLORY);

    assertFalse(store.consume(nonce, ALICE));
  }

  @Test
  public void shouldDiscardANonceRejectedForTheWrongUser() {
    String nonce = store.issue(MALLORY);

    assertFalse(store.consume(nonce, ALICE));
    // the failed attempt burned it, so the attacker cannot reuse it for their own flow either
    assertFalse(store.consume(nonce, MALLORY));
  }

  @Test
  public void shouldRejectANonceThisServerNeverIssued() {
    assertFalse(store.consume("Zm9yZ2VkLW5vbmNl", ALICE));
  }

  @Test
  public void shouldRejectAMissingNonce() {
    assertFalse(store.consume(null, ALICE));
    assertFalse(store.consume("", ALICE));
  }

  @Test
  public void shouldRejectAnUnknownUser() {
    assertFalse(store.consume(store.issue(ALICE), null));
    assertFalse(store.consume(store.issue(ALICE), ""));
  }

  @Test
  public void shouldIssueDistinctUrlSafeNonces() {
    Set<String> issued = new HashSet<>();
    for (int i = 0; i < 100; i++) {
      String nonce = store.issue(ALICE);
      assertTrue(
          nonce.matches("[A-Za-z0-9_-]{43}"),
          "Nonce must be 32 bytes of base64url without padding, but was: " + nonce);
      issued.add(nonce);
    }
    assertEquals(issued.size(), 100, "Nonces must not repeat");
  }

  // --- appendNonce -----------------------------------------------------------------------------

  @Test
  public void shouldAppendNonceToAUrlWithoutQuery() throws Exception {
    URL url =
        OAuthCsrfStateStore.appendNonce(new URL("http://eclipse.che/oauth/authenticate"), "n");

    assertEquals(url.toString(), "http://eclipse.che/oauth/authenticate?" + NONCE_PARAM + "=n");
  }

  @Test
  public void shouldKeepTheExistingQueryWhenAppendingTheNonce() throws Exception {
    URL url =
        OAuthCsrfStateStore.appendNonce(
            new URL("http://eclipse.che/oauth/authenticate?oauth_provider=github&scope=repo"), "n");

    assertEquals(
        url.toString(),
        "http://eclipse.che/oauth/authenticate?oauth_provider=github&scope=repo&"
            + NONCE_PARAM
            + "=n");
  }

  @Test
  public void shouldReplaceACallerSuppliedNonce() throws Exception {
    // UrlUtils#getParameter returns the first value of a repeated parameter, so a nonce planted by
    // the caller would shadow the issued one on readback if it were left in place
    URL url =
        OAuthCsrfStateStore.appendNonce(
            new URL(
                "http://eclipse.che/oauth/authenticate?"
                    + NONCE_PARAM
                    + "=planted&oauth_provider=github&"
                    + NONCE_PARAM
                    + "=planted-too"),
            "issued");

    assertEquals(
        url.toString(),
        "http://eclipse.che/oauth/authenticate?oauth_provider=github&" + NONCE_PARAM + "=issued");
  }

  @Test
  public void shouldNotReEncodeTheExistingQuery() throws Exception {
    // redirect_after_login carries an already encoded URL that may contain '{' and '}', which a
    // UriBuilder based implementation would re-encode or read as URI template parameters
    String query = "oauth_provider=github&redirect_after_login=https://che/f?url=%7B%22a%22:1%7D{}";
    URL url =
        OAuthCsrfStateStore.appendNonce(
            new URL("http://eclipse.che/oauth/authenticate?" + query), "n");

    assertEquals(
        url.toString(),
        "http://eclipse.che/oauth/authenticate?" + query + "&" + NONCE_PARAM + "=n");
  }

  // --- verifyCallback --------------------------------------------------------------------------

  @Test
  public void shouldAcceptACallbackCarryingTheIssuedNonce() throws Exception {
    String state = "oauth_provider=github&" + NONCE_PARAM + "=" + store.issue(ALICE);

    store.verifyCallback(callbackWithState(state));
  }

  @Test
  public void shouldRejectACallbackWithoutANonce() {
    // the pre-computed callback of an attacker: a valid looking state and their own code
    UriInfo uriInfo =
        callbackWithState("oauth_provider=github&redirect_after_login=https://che/dashboard");

    assertThrows(ForbiddenException.class, () -> store.verifyCallback(uriInfo));
  }

  @Test
  public void shouldRejectACallbackWithoutAnyState() {
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getRequestUri())
        .thenReturn(URI.create("http://eclipse.che/oauth/callback?code=c"));

    assertThrows(ForbiddenException.class, () -> store.verifyCallback(uriInfo));
  }

  @Test
  public void shouldRejectACallbackReplayedAfterASuccessfulOne() throws Exception {
    UriInfo uriInfo = callbackWithState(NONCE_PARAM + "=" + store.issue(ALICE));

    store.verifyCallback(uriInfo);

    assertThrows(ForbiddenException.class, () -> store.verifyCallback(uriInfo));
  }

  @Test
  public void shouldRejectACallbackForANonceIssuedToAnotherUser() {
    // Mallory starts a flow of their own and feeds the resulting callback URL to Alice
    UriInfo uriInfo = callbackWithState(NONCE_PARAM + "=" + store.issue(MALLORY));

    assertThrows(ForbiddenException.class, () -> store.verifyCallback(uriInfo));
  }

  // --- round trip through the state parameter --------------------------------------------------

  @Test
  public void shouldSurviveTheRoundTripThroughPrepareState() throws Exception {
    URL authenticateUrl =
        OAuthCsrfStateStore.appendNonce(
            new URL(
                "http://eclipse.che/oauth/authenticate?oauth_provider=github"
                    + "&redirect_after_login=https://che/dashboard%23/load-factory?url=x"),
            store.issue(ALICE));

    // what the authenticator hands to the provider, and what the provider hands back
    String state = new TestAuthenticator().state(authenticateUrl);

    store.verifyCallback(callbackWithEncodedState(state));
  }

  @Test
  public void shouldSurviveTheDoubleEncodingBitbucketServerDoesToTheState() throws Exception {
    URL authenticateUrl =
        OAuthCsrfStateStore.appendNonce(
            new URL("http://eclipse.che/oauth/authenticate?oauth_provider=bitbucket-server"),
            store.issue(ALICE));
    String state = new TestAuthenticator().state(authenticateUrl);

    // BitbucketOAuthAuthenticator encodes the state a second time, which Bitbucket Server undoes
    // before redirecting back, leaving the singly encoded value on the callback
    store.verifyCallback(callbackWithEncodedState(state));
  }

  // --- helpers ---------------------------------------------------------------------------------

  /** Reaches the protected {@link OAuthAuthenticator#prepareState(URL)}. */
  private static class TestAuthenticator extends OAuthAuthenticator {
    String state(URL requestUrl) {
      return prepareState(requestUrl);
    }

    @Override
    public String getOAuthProvider() {
      return "test";
    }

    @Override
    public String getEndpointUrl() {
      return "http://test";
    }
  }

  /** A callback request whose state is given decoded, as the tests of this class write it. */
  private static UriInfo callbackWithState(String state) {
    return callbackWithEncodedState(
        java.net.URLEncoder.encode(state, java.nio.charset.StandardCharsets.UTF_8));
  }

  /** A callback request whose state is given as it travels on the wire. */
  private static UriInfo callbackWithEncodedState(String encodedState) {
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getRequestUri())
        .thenReturn(
            URI.create("http://eclipse.che/oauth/callback?code=auth-code&state=" + encodedState));
    return uriInfo;
  }

  private static void setSubject(Subject subject) {
    EnvironmentContext context = new EnvironmentContext();
    context.setSubject(subject);
    EnvironmentContext.setCurrent(context);
  }
}
