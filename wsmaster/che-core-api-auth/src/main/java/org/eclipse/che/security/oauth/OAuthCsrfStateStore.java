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
import static org.eclipse.che.commons.lang.UrlUtils.getParameter;
import static org.eclipse.che.commons.lang.UrlUtils.getQueryParametersFromState;
import static org.eclipse.che.commons.lang.UrlUtils.getRequestUrl;
import static org.eclipse.che.commons.lang.UrlUtils.getState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import org.eclipse.che.api.core.ForbiddenException;
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
 * <p>{@link #issue(String)} binds an unguessable value to the user who started the flow; {@link
 * #verifyCallback(UriInfo)} requires the callback to return that value and consumes it, so a
 * callback that no user of this server asked for is rejected before any authorization code is
 * exchanged.
 *
 * <p>Pending nonces are held in memory. This matches how the rest of the OAuth implementation
 * already keeps per-flow state (the OAuth 2 credential {@code MemoryDataStoreFactory} and the OAuth
 * 1 shared token secrets), and like those it assumes a single che-server replica. A nonce that
 * cannot be found is treated as invalid, so the failure mode is a rejected callback and a retry,
 * never an accepted forgery.
 */
@Singleton
public class OAuthCsrfStateStore {
  private static final Logger LOG = LoggerFactory.getLogger(OAuthCsrfStateStore.class);

  /** Name of the {@code state} entry holding the nonce. */
  @VisibleForTesting static final String NONCE_PARAM = "csrf_nonce";

  /**
   * How long a started authorization flow may take to come back. Long enough for the user to log in
   * at the provider, pass a second factor and grant organization access; short enough that a nonce
   * leaked from a browser history or a proxy log is of little use.
   */
  private static final Duration NONCE_TTL = Duration.ofMinutes(15);

  /** Caps the memory an unauthenticated caller can make the server hold by hitting authenticate. */
  private static final int MAX_PENDING_NONCES = 10_000;

  private static final int NONCE_BYTES = 32;

  private final SecureRandom random = new SecureRandom();

  /** Nonce to the id of the user it was issued to. */
  private final Cache<String, String> pendingNonces =
      CacheBuilder.newBuilder().expireAfterWrite(NONCE_TTL).maximumSize(MAX_PENDING_NONCES).build();

  /**
   * Issues a nonce for {@code userId} and remembers it until it is consumed or expires.
   *
   * @return the nonce, consisting of base64url characters only, so that it survives the encoding
   *     and decoding the {@code state} parameter goes through on its way to the provider and back
   */
  public String issue(String userId) {
    byte[] bytes = new byte[NONCE_BYTES];
    random.nextBytes(bytes);
    String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    pendingNonces.put(nonce, userId);
    return nonce;
  }

  /**
   * Fails unless the {@code state} parameter of the callback request carries a nonce that this
   * server issued to the user of the current request. The nonce is consumed, so a callback URL
   * cannot be replayed.
   *
   * @throws ForbiddenException if the nonce is missing, unknown, already used, expired, or was
   *     issued to a different user
   */
  public void verifyCallback(UriInfo uriInfo) throws ForbiddenException {
    Map<String, List<String>> state = getQueryParametersFromState(getState(getRequestUrl(uriInfo)));
    String userId = currentUserId();
    if (!consume(getParameter(state, NONCE_PARAM), userId)) {
      // The nonce itself is not logged: it is a credential for the flow it belongs to.
      LOG.warn(
          "Rejected an OAuth callback for user '{}': the state parameter carries no valid nonce."
              + " The authorization flow was not started by this user on this server, or it took"
              + " longer than {} minutes.",
          userId,
          NONCE_TTL.toMinutes());
      throw new ForbiddenException(
          "Invalid OAuth state. The authorization request was not started by this user, or it has"
              + " expired. Please start the authorization again.");
    }
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

  /** Removes {@code nonce} and reports whether it had been issued to {@code userId}. */
  @VisibleForTesting
  boolean consume(String nonce, String userId) {
    if (isNullOrEmpty(nonce) || isNullOrEmpty(userId)) {
      return false;
    }
    // Removed whatever the outcome, so that a nonce is never usable twice.
    String issuedTo = pendingNonces.asMap().remove(nonce);
    return userId.equals(issuedTo);
  }

  /** The id the nonce of the current request is bound to. */
  static String currentUserId() {
    Subject subject = EnvironmentContext.getCurrent().getSubject();
    return subject == null ? null : subject.getUserId();
  }
}
