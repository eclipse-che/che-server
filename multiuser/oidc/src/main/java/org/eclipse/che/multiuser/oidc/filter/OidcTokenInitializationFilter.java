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

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.eclipse.che.multiuser.oidc.OIDCInfoProvider.OIDC_EMAIL_CLAIM_SETTING;
import static org.eclipse.che.multiuser.oidc.OIDCInfoProvider.OIDC_GROUPS_CLAIM_SETTING;
import static org.eclipse.che.multiuser.oidc.OIDCInfoProvider.OIDC_GROUP_PREFIX_SETTING;
import static org.eclipse.che.multiuser.oidc.OIDCInfoProvider.OIDC_USERNAME_CLAIM_SETTING;
import static org.eclipse.che.multiuser.oidc.OIDCInfoProvider.OIDC_USERNAME_PREFIX_SETTING;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.eclipse.che.multiuser.api.authentication.commons.SessionStore;
import org.eclipse.che.multiuser.api.authentication.commons.filter.MultiUserEnvironmentInitializationFilter;
import org.eclipse.che.multiuser.api.authentication.commons.token.RequestTokenExtractor;
import org.eclipse.che.multiuser.api.permission.server.AuthorizedSubject;
import org.eclipse.che.multiuser.api.permission.server.PermissionChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This filter uses given token directly. It's used for native Kubernetes user authentication.
 * Requests without token or with invalid token are rejected.
 *
 * <p>It also makes sure that User is present in Che database. If not, it will create the User from
 * JWT token claims. The username claim is configured with {@link
 * org.eclipse.che.multiuser.oidc.OIDCInfoProvider#OIDC_USERNAME_CLAIM_SETTING}. The email claim is
 * configured with {@link org.eclipse.che.multiuser.oidc.OIDCInfoProvider#OIDC_EMAIL_CLAIM_SETTING}.
 */
@Singleton
public class OidcTokenInitializationFilter
    extends MultiUserEnvironmentInitializationFilter<Jws<Claims>> {
  private static final Logger LOG = LoggerFactory.getLogger(OidcTokenInitializationFilter.class);

  private static final String EMAIL_CLAIM = "email";
  private static final String NAME_CLAIM = "name";
  private static final String GROUPS_CLAIM = "groups";
  protected static final String DEFAULT_USERNAME_CLAIM = NAME_CLAIM;
  protected static final String DEFAULT_EMAIL_CLAIM = EMAIL_CLAIM;
  protected static final String DEFAULT_GROUPS_CLAIM = GROUPS_CLAIM;

  private final JwtParser jwtParser;
  private final PermissionChecker permissionChecker;
  private final String usernameClaim;
  private final String usernamePrefix;
  private final String groupsClaim;
  private final String groupPrefix;
  private final String emailClaim;

  @Inject
  public OidcTokenInitializationFilter(
      PermissionChecker permissionChecker,
      JwtParser jwtParser,
      SessionStore sessionStore,
      RequestTokenExtractor tokenExtractor,
      @Nullable @Named(OIDC_USERNAME_CLAIM_SETTING) String usernameClaim,
      @Nullable @Named(OIDC_USERNAME_PREFIX_SETTING) String usernamePrefix,
      @Nullable @Named(OIDC_GROUPS_CLAIM_SETTING) String groupsClaim,
      @Nullable @Named(OIDC_GROUP_PREFIX_SETTING) String groupPrefix,
      @Nullable @Named(OIDC_EMAIL_CLAIM_SETTING) String emailClaim) {
    super(sessionStore, tokenExtractor);
    this.permissionChecker = permissionChecker;
    this.jwtParser = jwtParser;
    this.emailClaim = isNullOrEmpty(emailClaim) ? DEFAULT_EMAIL_CLAIM : emailClaim;
    this.usernameClaim = isNullOrEmpty(usernameClaim) ? DEFAULT_USERNAME_CLAIM : usernameClaim;
    this.usernamePrefix = isNullOrEmpty(usernamePrefix) ? "" : usernamePrefix;
    this.groupsClaim = isNullOrEmpty(groupsClaim) ? DEFAULT_GROUPS_CLAIM : groupsClaim;
    this.groupPrefix = isNullOrEmpty(groupPrefix) ? "" : groupPrefix;
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
    Claims claims = processedToken.getBody();
    String email = claims.get(emailClaim, String.class);
    String username = usernamePrefix + claims.get(usernameClaim, String.class);

    List<String> groups = new ArrayList<>();
    Object groupClaim = claims.get(this.groupsClaim);
    if (groupClaim instanceof Iterable) {
      for (Object group : ((Iterable<?>) groupClaim)) {
        groups.add(groupPrefix + group);
      }
    } else if (groupClaim instanceof String) {
      groups.add(groupPrefix + groupClaim);
    } else {
      LOG.warn("Groups claim '{}' is not a String or Iterable, skipping groups", this.groupsClaim);
    }

    return new AuthorizedSubject(
        new SubjectImpl(username, groups, claims.getSubject(), token, false), permissionChecker);
  }
}
