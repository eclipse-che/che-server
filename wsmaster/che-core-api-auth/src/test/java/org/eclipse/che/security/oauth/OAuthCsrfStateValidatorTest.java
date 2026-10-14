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
import static org.eclipse.che.security.oauth.OAuthCsrfStateValidator.COOKIE_NAME_PREFIX;
import static org.eclipse.che.security.oauth.OAuthCsrfStateValidator.HOST_PREFIX;
import static org.eclipse.che.security.oauth.OAuthCsrfStateValidator.NONCE_PARAM;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class OAuthCsrfStateValidatorTest {

  private static final String ALICE = "alice-id";
  private static final String MALLORY = "mallory-id";

  private OAuthCsrfStateValidator validator;
  private Browser browser;

  @BeforeMethod
  public void setUp() {
    validator = new OAuthCsrfStateValidator("https://eclipse.che/api");
    browser = new Browser();
    setSubject(ALICE);
  }

  @AfterMethod
  public void tearDown() {
    EnvironmentContext.reset();
  }

  // --- issueNonce ------------------------------------------------------------------------------

  @Test
  public void shouldIssueDistinctUrlSafeNonces() {
    Set<String> issued = new HashSet<>();
    for (int i = 0; i < 100; i++) {
      String nonce = validator.issueNonce();
      assertTrue(
          nonce.matches("[A-Za-z0-9_-]{43}"),
          "Nonce must be 32 bytes of base64url without padding, but was: " + nonce);
      issued.add(nonce);
    }
    assertEquals(issued.size(), 100, "Nonces must not repeat");
  }

  // --- stateCookie -----------------------------------------------------------------------------

  @Test
  public void shouldIssueAHostOnlyStateCookiePerProvider() {
    String cookie = validator.stateCookie("github", "a-nonce");

    assertTrue(
        cookie.startsWith(HOST_PREFIX + COOKIE_NAME_PREFIX + "github="),
        "Unexpected state cookie: " + cookie);
    // the __Host- prefix is honoured only on a Secure, host-only, Path=/ cookie, which is what
    // keeps a workspace on a sibling subdomain of the Che host from writing it
    assertTrue(cookie.contains("; Secure"), cookie);
    assertTrue(cookie.contains("; Path=/"), cookie);
    assertFalse(cookie.contains("Domain="), cookie);
    assertTrue(cookie.contains("; HttpOnly"), cookie);
    // Lax and not Strict: the callback is a top level navigation from the provider
    assertTrue(cookie.contains("; SameSite=Lax"), cookie);
    assertTrue(cookie.contains("; Max-Age=900"), cookie);
  }

  @Test
  public void shouldIssueAPlainStateCookieWhenCheIsNotServedOverHttps() {
    // a Secure cookie is dropped by the browser on a plain http page, and the __Host- prefix is
    // honoured only on a Secure cookie, so a http deployment has to do without both
    String cookie =
        new OAuthCsrfStateValidator("http://localhost:8080/api").stateCookie("github", "a-nonce");

    assertTrue(cookie.startsWith(COOKIE_NAME_PREFIX + "github="), "Unexpected cookie: " + cookie);
    assertFalse(cookie.contains("Secure"), cookie);
  }

  @Test
  public void shouldBindTheStateCookieToTheUserStartingTheFlow() {
    String nonce = validator.issueNonce();
    String alicesCookie = valueOf(validator.stateCookie("github", nonce));

    setSubject(MALLORY);

    assertNotEquals(
        valueOf(validator.stateCookie("github", nonce)),
        alicesCookie,
        "The state cookie of two users must not be interchangeable");
  }

  // --- verifyCallback --------------------------------------------------------------------------

  @Test
  public void shouldAcceptTheCallbackOfAFlowThisBrowserStarted() throws Exception {
    String nonce = startFlow("github");

    validator.verifyCallback(callbackWithState(stateOf("github", nonce)), browser.request());
  }

  @Test
  public void shouldRejectACallbackWithoutAStateCookie() {
    // the pre-computed callback of an attacker: a valid looking state and their own code. The
    // attacker cannot write a cookie for the Che host, so the browser of the victim presents none.
    UriInfo uriInfo =
        callbackWithState(
            "oauth_provider=github&redirect_after_login=https://che/dashboard&"
                + NONCE_PARAM
                + "="
                + validator.issueNonce());

    assertThrows(
        ForbiddenException.class, () -> validator.verifyCallback(uriInfo, browser.request()));
  }

  @Test
  public void shouldRejectACallbackWithoutANonceInTheState() throws Exception {
    startFlow("github");
    UriInfo uriInfo = callbackWithState("oauth_provider=github");

    assertThrows(
        ForbiddenException.class, () -> validator.verifyCallback(uriInfo, browser.request()));
  }

  @Test
  public void shouldRejectACallbackWithoutAnyState() throws Exception {
    startFlow("github");
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getRequestUri())
        .thenReturn(URI.create("http://eclipse.che/oauth/callback?code=c"));

    assertThrows(
        ForbiddenException.class, () -> validator.verifyCallback(uriInfo, browser.request()));
  }

  @Test
  public void shouldRejectACallbackCarryingANonceOfAnotherFlow() throws Exception {
    startFlow("github");
    // the attacker starts a flow of their own, in their own browser, to obtain a valid nonce
    String nonceOfAnotherFlow = validator.issueNonce();
    UriInfo uriInfo = callbackWithState(stateOf("github", nonceOfAnotherFlow));

    assertThrows(
        ForbiddenException.class, () -> validator.verifyCallback(uriInfo, browser.request()));
  }

  @Test
  public void shouldRejectACallbackWhoseCookieWasIssuedToAnotherUser() throws Exception {
    // Mallory starts a flow and hands the whole of it, cookie included, to Alice
    setSubject(MALLORY);
    String nonce = startFlow("github");
    setSubject(ALICE);
    UriInfo uriInfo = callbackWithState(stateOf("github", nonce));

    assertThrows(
        ForbiddenException.class, () -> validator.verifyCallback(uriInfo, browser.request()));
  }

  @Test
  public void shouldRejectACallbackNamingAProviderTheNonceWasNotIssuedFor() throws Exception {
    String nonce = startFlow("github");
    UriInfo uriInfo = callbackWithState(stateOf("gitlab", nonce));

    assertThrows(
        ForbiddenException.class, () -> validator.verifyCallback(uriInfo, browser.request()));
  }

  @Test
  public void shouldAcceptConcurrentFlowsToDifferentProviders() throws Exception {
    String githubNonce = startFlow("github");
    String gitlabNonce = startFlow("gitlab");

    validator.verifyCallback(callbackWithState(stateOf("gitlab", gitlabNonce)), browser.request());
    validator.verifyCallback(callbackWithState(stateOf("github", githubNonce)), browser.request());
  }

  @Test
  public void shouldRejectACallbackWhenTheRequestCarriesNoCookiesAtAll() {
    String nonce = startFlow("github");
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getCookies()).thenReturn(null);
    UriInfo uriInfo = callbackWithState(stateOf("github", nonce));

    assertThrows(ForbiddenException.class, () -> validator.verifyCallback(uriInfo, request));
  }

  // --- clearStateCookie ------------------------------------------------------------------------

  @Test
  public void shouldRejectACallbackReplayedAfterASuccessfulOne() throws Exception {
    String nonce = startFlow("github");
    UriInfo uriInfo = callbackWithState(stateOf("github", nonce));
    validator.verifyCallback(uriInfo, browser.request());

    // the browser applies the expiry the successful callback answered with
    browser.receive(
        validator
            .clearStateCookie(uriInfo, Response.ok().build())
            .getHeaderString(HttpHeaders.SET_COOKIE));

    assertThrows(
        ForbiddenException.class, () -> validator.verifyCallback(uriInfo, browser.request()));
  }

  @Test
  public void shouldKeepTheResponseOfTheCallbackWhenClearingTheStateCookie() {
    String nonce = startFlow("github");
    URI location = URI.create("https://eclipse.che/dashboard");

    Response response =
        validator.clearStateCookie(
            callbackWithState(stateOf("github", nonce)),
            Response.temporaryRedirect(location).build());

    assertEquals(response.getLocation(), location);
    assertEquals(response.getStatus(), Response.Status.TEMPORARY_REDIRECT.getStatusCode());
  }

  // --- appendNonce -----------------------------------------------------------------------------

  @Test
  public void shouldAppendNonceToAUrlWithoutQuery() throws Exception {
    URL url =
        OAuthCsrfStateValidator.appendNonce(new URL("http://eclipse.che/oauth/authenticate"), "n");

    assertEquals(url.toString(), "http://eclipse.che/oauth/authenticate?" + NONCE_PARAM + "=n");
  }

  @Test
  public void shouldKeepTheExistingQueryWhenAppendingTheNonce() throws Exception {
    URL url =
        OAuthCsrfStateValidator.appendNonce(
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
        OAuthCsrfStateValidator.appendNonce(
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
        OAuthCsrfStateValidator.appendNonce(
            new URL("http://eclipse.che/oauth/authenticate?" + query), "n");

    assertEquals(
        url.toString(),
        "http://eclipse.che/oauth/authenticate?" + query + "&" + NONCE_PARAM + "=n");
  }

  // --- round trip through the state parameter --------------------------------------------------

  @Test
  public void shouldSurviveTheRoundTripThroughPrepareState() throws Exception {
    String nonce = validator.issueNonce();
    browser.receive(validator.stateCookie("github", nonce));
    URL authenticateUrl =
        OAuthCsrfStateValidator.appendNonce(
            new URL(
                "http://eclipse.che/oauth/authenticate?oauth_provider=github"
                    + "&redirect_after_login=https://che/dashboard%23/load-factory?url=x"),
            nonce);

    // what the authenticator hands to the provider, and what the provider hands back
    String state = new TestAuthenticator().state(authenticateUrl);

    validator.verifyCallback(callbackWithEncodedState(state), browser.request());
  }

  @Test
  public void shouldSurviveTheDoubleEncodingBitbucketServerDoesToTheState() throws Exception {
    String nonce = validator.issueNonce();
    browser.receive(validator.stateCookie("bitbucket-server", nonce));
    URL authenticateUrl =
        OAuthCsrfStateValidator.appendNonce(
            new URL("http://eclipse.che/oauth/authenticate?oauth_provider=bitbucket-server"),
            nonce);
    String state = new TestAuthenticator().state(authenticateUrl);

    // BitbucketOAuthAuthenticator encodes the state a second time, which Bitbucket Server undoes
    // before redirecting back, leaving the singly encoded value on the callback
    validator.verifyCallback(callbackWithEncodedState(state), browser.request());
  }

  // --- helpers ---------------------------------------------------------------------------------

  /** Starts a flow for the current user: issues a nonce and hands the cookie to the browser. */
  private String startFlow(String oauthProvider) {
    String nonce = validator.issueNonce();
    browser.receive(validator.stateCookie(oauthProvider, nonce));
    return nonce;
  }

  private static String stateOf(String oauthProvider, String nonce) {
    return "oauth_provider=" + oauthProvider + "&" + NONCE_PARAM + "=" + nonce;
  }

  private static String valueOf(String setCookie) {
    return setCookie.split(";", 2)[0];
  }

  /** Keeps the cookies a {@code Set-Cookie} header hands it, and presents them back. */
  private static final class Browser {
    private final Map<String, String> cookies = new HashMap<>();

    void receive(String setCookie) {
      String pair = valueOf(setCookie);
      int equals = pair.indexOf('=');
      String name = pair.substring(0, equals);
      if (setCookie.contains("Max-Age=0")) {
        cookies.remove(name);
      } else {
        cookies.put(name, pair.substring(equals + 1));
      }
    }

    HttpServletRequest request() {
      HttpServletRequest request = mock(HttpServletRequest.class);
      when(request.getCookies())
          .thenReturn(
              cookies.entrySet().stream()
                  .map(entry -> new Cookie(entry.getKey(), entry.getValue()))
                  .toArray(Cookie[]::new));
      return request;
    }
  }

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
    return callbackWithEncodedState(URLEncoder.encode(state, UTF_8));
  }

  /** A callback request whose state is given as it travels on the wire. */
  private static UriInfo callbackWithEncodedState(String encodedState) {
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getRequestUri())
        .thenReturn(
            URI.create("http://eclipse.che/oauth/callback?code=auth-code&state=" + encodedState));
    return uriInfo;
  }

  private static void setSubject(String userId) {
    EnvironmentContext context = new EnvironmentContext();
    context.setSubject(new SubjectImpl(userId, emptyList(), userId, "token", false));
    EnvironmentContext.setCurrent(context);
  }
}
