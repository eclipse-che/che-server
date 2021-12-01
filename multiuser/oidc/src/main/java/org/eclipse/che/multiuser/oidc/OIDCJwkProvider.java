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

import com.auth0.jwk.GuavaCachedJwkProvider;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.google.common.base.Strings;
import java.net.MalformedURLException;
import java.net.URL;
import javax.inject.Inject;
import javax.inject.Provider;
import org.eclipse.che.inject.ConfigurationException;

/** Constructs {@link UrlJwkProvider} based on Jwk endpoint from keycloak settings */
public class OIDCJwkProvider implements Provider<JwkProvider> {

  private final JwkProvider jwkProvider;

  @Inject
  public OIDCJwkProvider(OIDCInfo oidcInfo) throws MalformedURLException {
    final String jwksUrl =
        Strings.isNullOrEmpty(oidcInfo.getJwksInternalUri())
            ? oidcInfo.getJwksPublicUri()
            : oidcInfo.getJwksInternalUri();

    if (jwksUrl == null) {
      throw new ConfigurationException("Jwks endpoint url not found in keycloak settings");
    }
    this.jwkProvider = new GuavaCachedJwkProvider(new UrlJwkProvider(new URL(jwksUrl)));
  }

  @Override
  public JwkProvider get() {
    return jwkProvider;
  }
}
