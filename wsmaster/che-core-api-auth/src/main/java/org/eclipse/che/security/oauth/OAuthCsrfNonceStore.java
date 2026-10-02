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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import javax.inject.Singleton;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Binds an OAuth 2.0 flow to the user who started it, with a nonce carried by the {@code state}
 * parameter.
 *
 * <p>The OAuth provider echoes the {@code state} back to the callback, and nothing else about a
 * callback request identifies the authorization it answers. Without a nonce, anyone can run the
 * authorization flow with their own SCM account, keep the resulting callback URL, and have a
 * logged-in Che user open it: the callback then exchanges the attacker's authorization code and
 * stores the resulting SCM token under the victim's Che identity, so the victim's workspaces act as
 * the attacker on that SCM provider (CWE-352).
 *
 * <p>A nonce is handed out when the flow starts and is remembered against the user it was issued
 * to. The callback is only served when the {@code state} carries a nonce this server issued, to the
 * user the callback request is authenticated as. A nonce is good for a single callback and for
 * {@link #LIFETIME} after it was issued, which is the time the user has to authorize Che at the
 * provider.
 *
 * <p>The nonces live in memory, as the Che server runs as a single replica and a flow that is
 * interrupted by a restart is one the user can simply start again.
 */
@Singleton
public class OAuthCsrfNonceStore {
  private static final Logger LOG = LoggerFactory.getLogger(OAuthCsrfNonceStore.class);

  /**
   * Name under which the nonce travels in the OAuth {@code state}, which is a copy of the query
   * string of the request that started the flow.
   */
  public static final String CSRF_NONCE_PARAM = "csrf_nonce";

  /** How long a nonce may be answered by a callback. */
  private static final Duration LIFETIME = Duration.ofMinutes(10);

  /** Upper bound on the flows waiting for a callback, so that the store cannot be grown at will. */
  private static final int MAX_PENDING_FLOWS = 10_000;

  private static final int NONCE_BYTES = 32;

  private final SecureRandom random = new SecureRandom();

  /** Nonce to the id of the user it was issued to. */
  private final Cache<String, String> issuedTo =
      CacheBuilder.newBuilder().expireAfterWrite(LIFETIME).maximumSize(MAX_PENDING_FLOWS).build();

  /**
   * Issues a nonce for a flow the given user is starting, to be carried by the {@code state} of the
   * authorization URL under {@link #CSRF_NONCE_PARAM}.
   *
   * @param subject the user starting the flow
   * @return the nonce, safe to put in a URL as is
   */
  public String issue(Subject subject) {
    byte[] bytes = new byte[NONCE_BYTES];
    random.nextBytes(bytes);
    String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    issuedTo.put(nonce, userIdOf(subject));
    return nonce;
  }

  /**
   * Consumes the nonce carried by the {@code state} of a callback and fails unless it was issued to
   * the given user, and has neither expired nor been used already.
   *
   * @param nonce the value of {@link #CSRF_NONCE_PARAM} in the {@code state}, may be {@code null}
   * @param subject the user the callback request is authenticated as
   * @throws ForbiddenException if the callback does not answer a flow this user started
   */
  public void verify(@Nullable String nonce, Subject subject) throws ForbiddenException {
    if (isNullOrEmpty(nonce)) {
      throw refuse("the state carries no nonce", subject);
    }
    // A nonce answers one callback: removing it here is what stops the same callback URL from
    // being replayed, and asMap() gives the atomicity that a get followed by an invalidate lacks.
    String issuedToUserId = issuedTo.asMap().remove(nonce);
    if (issuedToUserId == null) {
      throw refuse("the nonce is unknown, expired or already used", subject);
    }
    if (!issuedToUserId.equals(userIdOf(subject))) {
      throw refuse("the nonce was issued to another user", subject);
    }
  }

  /**
   * The user a nonce belongs to. A request without a subject has no user id to be compared by, so
   * it is given one of its own that no authenticated request can match.
   */
  private static String userIdOf(@Nullable Subject subject) {
    String userId = subject == null ? null : subject.getUserId();
    return userId == null ? "" : userId;
  }

  /** The nonce is chosen by the caller, so it is kept out of the message returned to it. */
  private ForbiddenException refuse(String reason, @Nullable Subject subject) {
    LOG.warn(
        "Blocked an OAuth callback to user '{}': {}",
        subject == null ? null : subject.getUserId(),
        reason);
    return new ForbiddenException(
        "The OAuth callback does not answer an authorization request of the current user");
  }
}
