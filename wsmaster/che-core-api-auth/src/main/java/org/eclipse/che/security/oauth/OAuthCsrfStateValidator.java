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

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.che.commons.lang.UrlUtils.getParameter;
import static org.eclipse.che.commons.lang.UrlUtils.getQueryParametersFromState;
import static org.eclipse.che.commons.lang.UrlUtils.getRequestUrl;
import static org.eclipse.che.commons.lang.UrlUtils.getState;

import com.google.common.annotations.VisibleForTesting;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Issues and verifies the single use CSRF nonce carried by the OAuth 2 {@code state} parameter.
 *
 * <p>{@link OAuthAuthenticator#prepareState(URL)} derives {@code state} solely from the query
 * string of the request that started the flow, so without a nonce the whole parameter is
 * attacker-computable. An attacker could then pre-build a callback URL carrying an authorization
 * code issued to their own SCM account, have a logged-in victim open it, and have {@link
 * EmbeddedOAuthAPI#callback(UriInfo, List)} persist the resulting SCM token under the victim's Che
 * identity (CWE-352).
 *
 * <p>The nonce is double submitted: {@link #issueNonce()} puts it in {@code state}, which travels
 * to the provider and back, and {@link #stateCookie(String, String)} puts the same nonce, bound to
 * the user starting the flow, in a cookie that only the browser that started the flow holds. {@link
 * #verifyCallback(UriInfo, HttpServletRequest)} requires the two to agree, so a callback that this
 * browser did not ask for is rejected before any authorization code is exchanged.
 *
 * <p>Nothing is held server side, so a callback does not have to reach the replica that served the
 * authenticate request it belongs to, and a restart in the middle of an authorization does not
 * invalidate it. Note that the OAuth 1 authenticator still keeps its shared token secrets between
 * {@code getAuthenticateUrl} and {@code callback} in memory, and that {@link
 * EmbeddedOAuthAPI#authenticate} remembers {@code redirect_after_login} in a field of a singleton,
 * so the OAuth 1 flow and the {@code access_denied} redirect of the OAuth 2 flow do still depend on
 * both requests reaching the same replica.
 *
 * <p>The cookie carries the {@value #HOST_PREFIX} prefix, which a browser honours only when the
 * cookie is {@code Secure}, host-only and {@code Path=/}. That is what keeps a workspace served
 * from a sibling subdomain of the Che host, which runs code the user wrote, from planting a cookie
 * for the Che host and pairing it with a {@code state} of its own. It is {@code SameSite=Lax} and
 * not {@code Strict} because the callback arrives as a cross site top level navigation from the
 * provider; the {@code <img>} variant of the forgery is a cross site subresource request, which
 * does not carry a {@code Lax} cookie at all.
 *
 * <p>A nonce that does not match is treated as invalid, whether the cookie expired, was already
 * consumed, or was never set because the flow started in another browser. The failure mode is
 * therefore a rejected callback and a retry, never an accepted forgery.
 */
@Singleton
public class OAuthCsrfStateValidator {
  private static final Logger LOG = LoggerFactory.getLogger(OAuthCsrfStateValidator.class);

  /** Name of the {@code state} entry holding the nonce. */
  @VisibleForTesting static final String NONCE_PARAM = "csrf_nonce";

  /** Name of the {@code state} entry naming the provider the flow belongs to. */
  private static final String PROVIDER_PARAM = "oauth_provider";

  /**
   * How long a started authorization flow may take to come back. Long enough for the user to log in
   * at the provider, pass a second factor, and grant organization access; short enough that a nonce
   * leaked from a browser history or a proxy log is of little use.
   */
  private static final Duration NONCE_TTL = Duration.ofMinutes(15);

  private static final int NONCE_BYTES = 32;

  /** Prefix a browser accepts only on a {@code Secure}, host-only, {@code Path=/} cookie. */
  @VisibleForTesting static final String HOST_PREFIX = "__Host-";

  /** One cookie per provider, so that flows to two providers can be in flight at once. */
  @VisibleForTesting static final String COOKIE_NAME_PREFIX = "che_oauth_state_";

  /** Keeps a provider name taken from the untrusted {@code state} from growing the header. */
  private static final int MAX_PROVIDER_NAME_LENGTH = 64;

  private final SecureRandom random = new SecureRandom();

  /** Whether the browser reaches Che over https, so the cookie can be {@code Secure}. */
  private final boolean secureCookie;

  @Inject
  public OAuthCsrfStateValidator(@Named("che.api") String apiEndpoint) {
    this.secureCookie = isHttps(apiEndpoint);
  }

  /**
   * Returns a fresh nonce for the authorization flow that is about to start.
   *
   * @return the nonce, consisting of base64url characters only, so that it survives the encoding
   *     and decoding the {@code state} parameter goes through on its way to the provider and back
   */
  public String issueNonce() {
    byte[] bytes = new byte[NONCE_BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * Returns the {@code Set-Cookie} header that hands {@code nonce} to the browser starting the
   * flow, bound to the user of the current request. The callback is accepted only if it comes back
   * with both this cookie and the same nonce in its {@code state}.
   */
  public String stateCookie(String oauthProvider, String nonce) {
    // A flow started without a user to bind the nonce to gets an empty cookie rather than none, so
    // that it fails on the callback instead of falling back on a cookie of an earlier flow.
    return cookie(
        oauthProvider, nullToEmpty(binding(currentUserId(), nonce)), NONCE_TTL.toSeconds());
  }

  /**
   * Fails unless the {@code state} parameter of the callback request carries the nonce of the
   * cookie the browser presents, and that cookie was issued to the user of the current request.
   *
   * @param request the callback request, read for its cookies only
   * @throws ForbiddenException if the nonce or the cookie is missing, the two disagree, or the
   *     cookie was issued to a different user
   */
  public void verifyCallback(UriInfo uriInfo, @Nullable HttpServletRequest request)
      throws ForbiddenException {
    Map<String, List<String>> state = stateOf(uriInfo);
    String userId = currentUserId();
    String expected = binding(userId, getParameter(state, NONCE_PARAM));
    String presented = presentedBinding(request, getParameter(state, PROVIDER_PARAM));
    if (expected == null || presented == null || !constantTimeEquals(expected, presented)) {
      // Neither the nonce nor the cookie is logged: both are credentials for the flow they belong
      // to.
      LOG.warn(
          "Rejected an OAuth callback for user '{}': the state parameter and the state cookie do"
              + " not carry the same nonce. The authorization flow was not started by this user in"
              + " this browser, or it took longer than {} minutes.",
          userId,
          NONCE_TTL.toMinutes());
      throw new ForbiddenException(
          "Invalid OAuth state. The authorization request was not started by this user, or it has"
              + " expired. Please start the authorization again.");
    }
  }

  /**
   * Returns {@code response} with the state cookie of the verified callback expired, so that a
   * callback URL cannot be replayed. Call it only once {@link #verifyCallback(UriInfo,
   * HttpServletRequest)} has accepted the request.
   */
  public Response clearStateCookie(UriInfo uriInfo, Response response) {
    String oauthProvider = getParameter(stateOf(uriInfo), PROVIDER_PARAM);
    return Response.fromResponse(response)
        .header(HttpHeaders.SET_COOKIE, cookie(oauthProvider, "", 0))
        .build();
  }

  /**
   * Returns {@code requestUrl} with the nonce appended to its query string, replacing any nonce the
   * caller supplied. The nonce has to go into the query because {@link
   * OAuthAuthenticator#prepareState(URL)} is what turns the query into the {@code state} parameter,
   * and every provider builds its authorization URL through it.
   */
  static URL appendNonce(URL requestUrl, String nonce) throws MalformedURLException {
    String url = requestUrl.toString();
    int queryStart = url.indexOf('?');
    String base = queryStart < 0 ? url : url.substring(0, queryStart);
    // Rebuilt by hand rather than through UriBuilder: the query carries values such as
    // redirect_after_login that are already encoded and may contain '{' and '}', which UriBuilder
    // would re-encode or read as URI template parameters.
    String query = queryStart < 0 ? "" : stripNonce(url.substring(queryStart + 1));
    StringBuilder rebuilt = new StringBuilder(base).append('?');
    if (!query.isEmpty()) {
      rebuilt.append(query).append('&');
    }
    return new URL(rebuilt.append(NONCE_PARAM).append('=').append(nonce).toString());
  }

  /**
   * Drops any nonce the caller put in the query. {@link
   * org.eclipse.che.commons.lang.UrlUtils#getParameter(Map, String)} returns the first value of a
   * repeated parameter, so a caller-supplied one would otherwise shadow the issued nonce on
   * readback.
   */
  private static String stripNonce(String query) {
    StringBuilder kept = new StringBuilder();
    for (String pair : query.split("&")) {
      if (pair.isEmpty() || pair.equals(NONCE_PARAM) || pair.startsWith(NONCE_PARAM + "=")) {
        continue;
      }
      if (kept.length() > 0) {
        kept.append('&');
      }
      kept.append(pair);
    }
    return kept.toString();
  }

  /** The parameters the {@code state} of the given callback request carries. */
  private static Map<String, List<String>> stateOf(UriInfo uriInfo) {
    return getQueryParametersFromState(getState(getRequestUrl(uriInfo)));
  }

  /**
   * The cookie value that ties {@code nonce} to {@code userId}, or {@code null} if either is
   * missing. The user id is base64url encoded, both to keep it out of the cookie in clear and
   * because it is free to contain characters a cookie value may not.
   */
  @Nullable
  private static String binding(@Nullable String userId, @Nullable String nonce) {
    if (isNullOrEmpty(userId) || isNullOrEmpty(nonce)) {
      return null;
    }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(userId.getBytes(UTF_8))
        + "."
        + nonce;
  }

  /** The value of the state cookie of {@code oauthProvider}, or {@code null} if there is none. */
  @Nullable
  private String presentedBinding(
      @Nullable HttpServletRequest request, @Nullable String oauthProvider) {
    if (request == null || isNullOrEmpty(oauthProvider)) {
      return null;
    }
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    String name = cookieName(oauthProvider);
    for (Cookie cookie : cookies) {
      if (name.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }

  /** Builds the {@code Set-Cookie} header of the state cookie of {@code oauthProvider}. */
  private String cookie(@Nullable String oauthProvider, String value, long maxAgeSeconds) {
    StringBuilder cookie =
        new StringBuilder(cookieName(oauthProvider))
            .append('=')
            .append(value)
            .append("; Max-Age=")
            .append(maxAgeSeconds)
            // Path=/ and the absence of a Domain are what the __Host- prefix requires. The cookie
            // is sent with every request to the Che host as a result, which is the price of
            // keeping a sibling subdomain from writing it.
            .append("; Path=/; HttpOnly")
            // Lax, not Strict: the callback is a top level navigation from the provider, which a
            // Strict cookie would not be sent with.
            .append("; SameSite=Lax");
    if (secureCookie) {
      cookie.append("; Secure");
    }
    return cookie.toString();
  }

  /**
   * The name of the state cookie of {@code oauthProvider}. The provider is read from the untrusted
   * {@code state} on the callback, so anything that is not a cookie name character is folded to an
   * underscore. Two names that fold together would at worst overwrite each other's cookie, which
   * costs a rejected callback and a retry; no registered provider name needs folding.
   */
  private String cookieName(@Nullable String oauthProvider) {
    StringBuilder name =
        new StringBuilder(secureCookie ? HOST_PREFIX : "").append(COOKIE_NAME_PREFIX);
    String provider = nullToEmpty(oauthProvider);
    for (int i = 0; i < Math.min(provider.length(), MAX_PROVIDER_NAME_LENGTH); i++) {
      char c = provider.charAt(i);
      boolean isNameChar =
          (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-';
      name.append(isNameChar ? c : '_');
    }
    return name.toString();
  }

  /** Compares two nonces without leaking how far they match through the time taken. */
  private static boolean constantTimeEquals(String expected, String presented) {
    return MessageDigest.isEqual(expected.getBytes(UTF_8), presented.getBytes(UTF_8));
  }

  /**
   * Whether {@code che.api}, which is the URL the browser reaches Che at, uses https. A {@code
   * Secure} cookie is dropped by a browser on a plain http page, and the {@value #HOST_PREFIX}
   * prefix in turn is honoured only on a {@code Secure} cookie, so a deployment served over http
   * has to do without both.
   */
  private static boolean isHttps(String apiEndpoint) {
    try {
      if ("https".equalsIgnoreCase(new URI(nullToEmpty(apiEndpoint).trim()).getScheme())) {
        return true;
      }
    } catch (URISyntaxException e) {
      LOG.error("`che.api` ('{}') is not parseable: {}", apiEndpoint, e.getMessage());
    }
    LOG.warn(
        "`che.api` ('{}') is not an https URL, so the OAuth state cookie is set without the"
            + " `Secure` attribute and the `{}` prefix. A deployment that serves Che over https"
            + " has to set `che.api` to its https URL for the OAuth callback to be protected"
            + " against a sibling subdomain.",
        apiEndpoint,
        HOST_PREFIX);
    return false;
  }

  /** The id the nonce of the current request is bound to. */
  static String currentUserId() {
    Subject subject = EnvironmentContext.getCurrent().getSubject();
    return subject == null ? null : subject.getUserId();
  }
}
