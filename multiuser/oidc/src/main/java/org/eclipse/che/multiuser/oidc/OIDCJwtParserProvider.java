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
package org.eclipse.che.multiuser.oidc;

import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SigningKeyResolver;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

/** Provides instance of {@link JwtParser} */
@Singleton
public class OIDCJwtParserProvider implements Provider<JwtParser> {
  private final JwtParser jwtParser;

  @Inject
  public OIDCJwtParserProvider(
      @Named("che.keycloak.allowed_clock_skew_sec") long allowedClockSkewSec,
      SigningKeyResolver signingKeyResolver) {
    this.jwtParser =
        Jwts.parserBuilder()
            .setSigningKeyResolver(signingKeyResolver)
            .setAllowedClockSkewSeconds(allowedClockSkewSec)
            .build();
  }

  @Override
  public JwtParser get() {
    return jwtParser;
  }
}
