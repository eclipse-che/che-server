/*
 * Copyright (c) 2012-2025 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.workspace.infrastructure.kubernetes.multiuser.oauth;

import static com.google.common.base.Strings.isNullOrEmpty;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.user.User;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.commons.subject.SubjectImpl;

/**
 * This filter uses given token directly. It's used for native Kubernetes user authentication.
 * Requests without token or with invalid token are rejected.
 *
 * <p>It also makes sure that User is present in Che database. If not, it will create the User from
 * JWT token claims. The username claim is configured with {@link
 * OidcTokenInitializationFilter#OIDC_USERNAME_CLAIM_SETTING}. The email claim is configured with
 * {@link OidcTokenInitializationFilter#OIDC_EMAIL_CLAIM_SETTING}.
 */
@Singleton
public class OidcTokenInitializationFilter
    extends MultiUserEnvironmentInitializationFilter<Jws<Claims>> {
  private static final String EMAIL_CLAIM = "email";
  private static final String NAME_CLAIM = "name";
  protected static final String DEFAULT_USERNAME_CLAIM = NAME_CLAIM;
  protected static final String DEFAULT_EMAIL_CLAIM = EMAIL_CLAIM;

  private static final String OIDC_SETTING_PREFIX = "che.oidc.";
  private static final String OIDC_EMAIL_CLAIM_SETTING = OIDC_SETTING_PREFIX + "email_claim";
  private static final String OIDC_USERNAME_CLAIM_SETTING = OIDC_SETTING_PREFIX + "username_claim";

  private final JwtParser jwtParser;
  private final UserManager userManager;
  private final String usernameClaim;
  private final String emailClaim;

  @Inject
  public OidcTokenInitializationFilter(
      JwtParser jwtParser,
      SessionStore sessionStore,
      RequestTokenExtractor tokenExtractor,
      UserManager userManager,
      @Nullable @Named(OIDC_USERNAME_CLAIM_SETTING) String usernameClaim,
      @Nullable @Named(OIDC_EMAIL_CLAIM_SETTING) String emailClaim) {
    super(sessionStore, tokenExtractor);
    this.jwtParser = jwtParser;
    this.userManager = userManager;
    this.usernameClaim = isNullOrEmpty(usernameClaim) ? DEFAULT_USERNAME_CLAIM : usernameClaim;
    this.emailClaim = isNullOrEmpty(emailClaim) ? DEFAULT_EMAIL_CLAIM : emailClaim;
  }

  @Override
  protected Optional<Jws<Claims>> processToken(String token) {
    return Optional.ofNullable(jwtParser.parseClaimsJws(token));
  }

  @Override
  protected String getUserId(Jws<Claims> processedToken) {
    return processedToken.getBody().getSubject();
  }

  @Override
  protected Subject extractSubject(String token, Jws<Claims> processedToken) {
    try {
      Claims claims = processedToken.getBody();
      User user =
          userManager.getOrCreateUser(
              claims.getSubject(),
              claims.get(emailClaim, String.class),
              claims.get(usernameClaim, String.class));
      return new AuthorizedSubject(new SubjectImpl(user.getName(), user.getId(), token, false));
    } catch (ServerException | ConflictException e) {
      throw new RuntimeException(e);
    }
  }
}
