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
package org.eclipse.che.multiuser.keycloak.server;

import static org.eclipse.che.multiuser.machine.authentication.shared.Constants.MACHINE_TOKEN_KIND;

import com.auth0.jwk.JwkProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import java.security.Key;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.multiuser.oidc.OIDCSigningKeyResolver;

/** Resolves signing key based on id from JWT header */
@Singleton
public class KeycloakSigningKeyResolver extends OIDCSigningKeyResolver {
  @Inject
  KeycloakSigningKeyResolver(JwkProvider jwkProvider) {
    super(jwkProvider);
  }

  @Override
  public Key resolveSigningKey(JwsHeader header, String plaintext) {
    if (MACHINE_TOKEN_KIND.equals(header.get("kind"))) {
      throw new MachineTokenJwtException(); // machine token, doesn't need to verify
    }
    return getJwtPublicKey(header);
  }

  @Override
  public Key resolveSigningKey(JwsHeader header, Claims claims) {
    if (MACHINE_TOKEN_KIND.equals(header.get("kind"))) {
      throw new MachineTokenJwtException(); // machine token, doesn't need to verify
    }
    return getJwtPublicKey(header);
  }
}
