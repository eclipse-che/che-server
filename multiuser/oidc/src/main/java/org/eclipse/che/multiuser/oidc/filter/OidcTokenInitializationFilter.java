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

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.eclipse.che.multiuser.oidc.OIDCInfoProvider.OIDC_USERNAME_CLAIM_SETTING;

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
import org.eclipse.che.multiuser.api.authentication.commons.SessionStore;
import org.eclipse.che.multiuser.api.authentication.commons.filter.MultiUserEnvironmentInitializationFilter;
import org.eclipse.che.multiuser.api.authentication.commons.token.RequestTokenExtractor;
import org.eclipse.che.multiuser.api.permission.server.AuthorizedSubject;
import org.eclipse.che.multiuser.api.permission.server.PermissionChecker;

/**
 * This filter uses given token directly. It's used for native Kubernetes user authentication.
 * Requests without token or with invalid token are rejected.
 *
 * <p>It also makes sure that User is present in Che database. If not, it will create the User from
 * JWT token claims. The username claim is configured with {@link
 * org.eclipse.che.multiuser.oidc.OIDCInfoProvider#OIDC_USERNAME_CLAIM_SETTING}.
 */
@Singleton
public class OidcTokenInitializationFilter
    extends MultiUserEnvironmentInitializationFilter<Jws<Claims>> {
  private static final String EMAIL_CLAIM = "email";
  private static final String NAME_CLAIM = "name";
  protected static final String DEFAULT_USERNAME_CLAIM = NAME_CLAIM;

  private final JwtParser jwtParser;
  private final PermissionChecker permissionChecker;
  private final UserManager userManager;
  private final String usernameClaim;

  @Inject
  public OidcTokenInitializationFilter(
      PermissionChecker permissionChecker,
      JwtParser jwtParser,
      SessionStore sessionStore,
      RequestTokenExtractor tokenExtractor,
      UserManager userManager,
      @Nullable @Named(OIDC_USERNAME_CLAIM_SETTING) String usernameClaim) {
    super(sessionStore, tokenExtractor);
    this.permissionChecker = permissionChecker;
    this.jwtParser = jwtParser;
    this.userManager = userManager;
    this.usernameClaim = isNullOrEmpty(usernameClaim) ? DEFAULT_USERNAME_CLAIM : usernameClaim;
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
              claims.get(EMAIL_CLAIM, String.class),
              claims.get(usernameClaim, String.class));
      return new AuthorizedSubject(
          new SubjectImpl(user.getName(), user.getId(), token, false), permissionChecker);
    } catch (ServerException | ConflictException e) {
      throw new RuntimeException(e);
    }
  }
}
